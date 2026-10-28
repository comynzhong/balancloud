#!/usr/bin/env python3
"""OCPP 1.6J 充电桩模拟器（直流 / 四轮交流）。

只实现「能形成一笔完整订单」的消息子集：BootNotification / Heartbeat /
StatusNotification / Authorize / StartTransaction / MeterValues / StopTransaction，
并应答平台下发的 RemoteStartTransaction / RemoteStopTransaction / UnlockConnector。

用法：
    # 直流：等后台/小程序远程启动
    python tools/ocpp-sim/sim.py --url ws://127.0.0.1:8082 --identity CP-DC-001
    # 交流即插即充：Boot 后自己插枪，用 VIN 当 idTag
    python tools/ocpp-sim/sim.py --identity CP-AC-001 --mode ac --plug-and-charge --vin LSVAU2180N2123456

依赖 websocket-client（pip install websocket-client）。
"""

import argparse
import base64
import json
import random
import sys
import time
from datetime import datetime, timezone

import websocket


def now_iso():
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def log(message):
    print(f"[{now_iso()}] {message}", flush=True)


class ChargePoint:
    def __init__(self, args):
        self.args = args
        self.url = resolve_url(args.url, args.identity)
        self.ws = None
        self.seq = 0
        self.pending = {}
        self.actions = []
        self.voltage = 230.0 if args.mode == "ac" else 750.0
        self.model = "SIM-AC" if args.mode == "ac" else "SIM-DC"
        self.id_tag = args.vin or "BALAN"

        self.transaction_id = None
        self.connector_id = 1
        self.meter_wh = args.meter_start_wh
        self.charging = False
        self.session_start = 0.0
        self.next_meter_at = 0.0
        self.soc = 30
        self.faulted = False
        self.fault_injected = False
        self.stop_sent = False
        self.stopping = False
        self.stopped_meter_wh = 0
        self.plugged = False
        self.plug_scheduled = False

    # ---------- 连接与报文 ----------

    def connect(self):
        headers = []
        if self.args.user:
            token = base64.b64encode(
                f"{self.args.user}:{self.args.password}".encode("utf-8")).decode("ascii")
            headers.append(f"Authorization: Basic {token}")
        log(f"connecting {self.url} (subprotocol ocpp1.6)")
        self.ws = websocket.create_connection(
            self.url,
            subprotocols=["ocpp1.6"],
            timeout=5,
            header=headers or None,
        )
        self.ws.settimeout(1.0)
        log(f"connected, negotiated {self.ws.getsubprotocol()}")

    def send_call(self, action, payload):
        self.seq += 1
        unique_id = str(self.seq)
        self.pending[unique_id] = action
        self.ws.send(json.dumps([2, unique_id, action, payload]))
        log(f"CALL   {action} id={unique_id} {json.dumps(payload, ensure_ascii=False)}")
        return unique_id

    def send_result(self, unique_id, payload):
        self.ws.send(json.dumps([3, unique_id, payload]))
        log(f"RESULT id={unique_id} {json.dumps(payload, ensure_ascii=False)}")

    def send_error(self, unique_id, code, description):
        self.ws.send(json.dumps([4, unique_id, code, description, {}]))
        log(f"ERROR  id={unique_id} {code} {description}")

    # ---------- 业务动作 ----------

    def run(self):
        self.connect()
        self.send_call("BootNotification", self.boot_payload())
        last_heartbeat = time.time()
        while True:
            now = time.time()
            if now - last_heartbeat >= self.args.heartbeat:
                self.send_call("Heartbeat", {})
                last_heartbeat = now
            self.tick(now)
            try:
                message = self.ws.recv()
            except websocket.WebSocketTimeoutException:
                continue
            if message is None or message == "":
                raise ConnectionError("服务端关闭了连接")
            self.handle(message)

    def tick(self, now):
        due = [item for item in self.actions if item[0] <= now]
        if due:
            self.actions = [item for item in self.actions if item[0] > now]
            for _, action in due:
                action()
        if not self.charging:
            return
        if self.stopping:
            return
        if self.args.fault == "disconnect" and not self.fault_injected \
                and now - self.session_start >= self.args.fault_after:
            self.fault_injected = True
            log("fault injected: dropping connection mid-transaction")
            self.ws.close()
            raise ConnectionError("模拟断连")
        if self.args.fault == "faulted" and not self.fault_injected \
                and now - self.session_start >= self.args.fault_after:
            self.fault_injected = True
            self.faulted = True
            self.notify_status(self.connector_id, "Faulted")
            self.stop_transaction("EmergencyStop")
            return
        if self.args.stop_after > 0 and now - self.session_start >= self.args.stop_after \
                and not self.stop_sent:
            self.stop_transaction("Local")
            return
        if now >= self.next_meter_at:
            self.next_meter_at = now + self.args.interval
            self.meter_wh += self.args.meter_step_wh
            self.soc = min(100, self.soc + 1)
            self.send_meter_values()

    def later(self, delay, action):
        self.actions.append((time.time() + delay, action))

    def handle(self, raw):
        try:
            message = json.loads(raw)
        except ValueError:
            log(f"忽略非法 JSON: {raw[:120]}")
            return
        if not isinstance(message, list) or not message:
            log(f"忽略非数组报文: {raw[:120]}")
            return
        kind = message[0]
        if kind == 2:
            self.handle_call(message)
        elif kind == 3:
            self.handle_result(message)
        elif kind == 4:
            self.handle_error(message)
        else:
            log(f"忽略未知报文类型 {kind}")

    def handle_call(self, message):
        unique_id = message[1]
        action = message[2] if len(message) > 2 else ""
        payload = message[3] if len(message) > 3 else {}
        log(f"CALL   <- {action} id={unique_id} {json.dumps(payload, ensure_ascii=False)}")
        if action == "RemoteStartTransaction":
            self.send_result(unique_id, {"status": "Accepted"})
            self.connector_id = int(payload.get("connectorId") or 1)
            id_tag = payload.get("idTag") or self.id_tag
            self.later(0.4, lambda: self.notify_status(self.connector_id, "Preparing"))
            self.later(1.2, lambda: self.start_transaction(self.connector_id, id_tag))
        elif action == "RemoteStopTransaction":
            self.send_result(unique_id, {"status": "Accepted"})
            self.later(0.2, lambda: self.stop_transaction("Remote"))
        elif action == "UnlockConnector":
            self.send_result(unique_id, {"status": "Unlocked"})
        else:
            self.send_error(unique_id, "NotImplemented", f"Unsupported action {action}")

    def handle_result(self, message):
        unique_id = message[1]
        payload = message[2] if len(message) > 2 else {}
        action = self.pending.pop(unique_id, "")
        log(f"RESULT <- {action or '?'} id={unique_id} {json.dumps(payload, ensure_ascii=False)}")
        if action == "BootNotification":
            if payload.get("status") == "Accepted":
                for connector in range(1, self.args.connectors + 1):
                    self.notify_status(connector, "Available")
                if self.args.plug_and_charge and not self.plug_scheduled:
                    self.plug_scheduled = True
                    self.later(self.args.plug_after, self.plug_in)
            else:
                log("BootNotification 未接受，30 秒后重试")
                self.later(30, self.reboot)
        elif action == "StartTransaction":
            transaction_id = payload.get("transactionId")
            tag_info = payload.get("idTagInfo") or {}
            if tag_info.get("status", "Accepted") != "Accepted" or not transaction_id:
                log("StartTransaction 被拒绝，回到 Available")
                self.notify_status(self.connector_id, "Available")
                return
            self.transaction_id = int(transaction_id)
            self.charging = True
            self.session_start = time.time()
            self.next_meter_at = self.session_start + self.args.interval
            self.stop_sent = False
            log(f"transaction {self.transaction_id} started at {self.meter_wh} Wh")
            self.notify_status(self.connector_id, "Charging")
        elif action == "StopTransaction":
            log(f"transaction {self.transaction_id} stopped at {self.stopped_meter_wh} Wh")
            self.charging = False
            self.stopping = False
            self.transaction_id = None
            self.after_stop()

    def after_stop(self):
        if self.faulted:
            self.notify_status(self.connector_id, "Faulted")
            return
        if self.args.stay_plugged:
            # 枪还插着：报 SuspendedEV 让平台进入占位；unplug-after=0 表示一直占着。
            self.notify_status(self.connector_id, "SuspendedEV")
            log("still plugged; occupancy starts")
            if self.args.unplug_after > 0:
                self.later(self.args.unplug_after, self.unplug)
            return
        self.notify_status(self.connector_id, "Available")

    def unplug(self):
        self.plugged = False
        self.notify_status(self.connector_id, "Available")
        log("unplugged")

    def handle_error(self, message):
        unique_id = message[1]
        action = self.pending.pop(unique_id, "")
        code = message[2] if len(message) > 2 else ""
        description = message[3] if len(message) > 3 else ""
        log(f"ERROR  <- {action or '?'} id={unique_id} {code} {description}")

    # ---------- OCPP 消息 ----------

    def reboot(self):
        self.send_call("BootNotification", self.boot_payload())

    def boot_payload(self):
        return {
            "chargePointVendor": "BALAN",
            "chargePointModel": self.model,
            "chargePointSerialNumber": self.args.identity,
            "firmwareVersion": "sim-1.0",
        }

    def plug_in(self):
        """即插即充：先 Preparing 占住端口，再 Authorize + StartTransaction。"""
        if self.plugged or self.charging:
            return
        self.plugged = True
        self.notify_status(self.connector_id, "Preparing")
        log(f"plugged in, idTag={self.id_tag}")
        self.later(1.0, lambda: self.start_transaction(self.connector_id, self.id_tag))

    def notify_status(self, connector_id, status):
        self.send_call("StatusNotification", {
            "connectorId": connector_id,
            "errorCode": "NoError",
            "status": status,
            "timestamp": now_iso(),
        })

    def start_transaction(self, connector_id, id_tag):
        if self.charging:
            return
        self.send_call("Authorize", {"idTag": id_tag})
        self.send_call("StartTransaction", {
            "connectorId": connector_id,
            "idTag": id_tag,
            "meterStart": self.meter_wh,
            "timestamp": now_iso(),
        })

    def send_meter_values(self):
        power_w = self.args.power_kw * 1000.0
        current = round(power_w / self.voltage, 1)
        self.send_call("MeterValues", {
            "connectorId": self.connector_id,
            "transactionId": self.transaction_id,
            "meterValue": [{
                "timestamp": now_iso(),
                "sampledValue": [
                    {"value": str(self.meter_wh), "measurand": "Energy.Active.Import.Register", "unit": "Wh"},
                    {"value": str(int(power_w)), "measurand": "Power.Active.Import", "unit": "W"},
                    {"value": str(self.voltage), "measurand": "Voltage", "unit": "V"},
                    {"value": str(current), "measurand": "Current.Import", "unit": "A"},
                    {"value": str(self.soc), "measurand": "SoC", "unit": "Percent"},
                    {"value": str(round(35 + random.uniform(0, 3), 1)), "measurand": "Temperature", "unit": "Celsius"},
                ],
            }],
        })

    def stop_transaction(self, reason):
        if not self.charging or self.stop_sent:
            return
        self.stop_sent = True
        self.stopping = True
        self.stopped_meter_wh = self.meter_wh
        self.notify_status(self.connector_id, "Finishing")
        self.send_call("StopTransaction", {
            "transactionId": self.transaction_id,
            "meterStop": self.stopped_meter_wh,
            "timestamp": now_iso(),
            "reason": reason,
        })


def resolve_url(url, identity):
    base = url.rstrip("/")
    if "{identity}" in base:
        return base.replace("{identity}", identity)
    if base.endswith("/ocpp"):
        return f"{base}/{identity}"
    return f"{base}/ocpp/{identity}"


def parse_args(argv):
    parser = argparse.ArgumentParser(description="OCPP 1.6J 直流/交流桩模拟器")
    parser.add_argument("--url", default="ws://127.0.0.1:8082",
                        help="服务端地址；自动追加 /ocpp/{identity}")
    parser.add_argument("--identity", required=True, help="ChargePoint identity（即设备编号）")
    parser.add_argument("--mode", choices=["dc", "ac"], default="dc",
                        help="dc=直流 750V / ac=交流 230V（默认 dc）")
    parser.add_argument("--connectors", type=int, default=1, help="枪数，1-4")
    parser.add_argument("--power-kw", type=float, default=None,
                        help="恒功率（kW）；缺省直流 120、交流 7.4")
    parser.add_argument("--meter-step-wh", type=int, default=None,
                        help="每个上报周期的电量增量（Wh）；缺省按功率与周期推导")
    parser.add_argument("--meter-start-wh", type=int, default=0, help="起始电表（Wh）")
    parser.add_argument("--interval", type=int, default=5, help="MeterValues 周期（秒）")
    parser.add_argument("--heartbeat", type=int, default=30, help="Heartbeat 周期（秒）")
    parser.add_argument("--stop-after", type=int, default=0,
                        help="单次会话最长充电秒数；0 表示只等 RemoteStop")
    parser.add_argument("--fault", choices=["none", "faulted", "disconnect"], default="none",
                        help="故障注入：Faulted 状态 / 中途断连")
    parser.add_argument("--fault-after", type=int, default=20, help="充电多少秒后注入故障")
    parser.add_argument("--vin", default="", help="即插即充的 idTag（17 位 VIN）")
    parser.add_argument("--plug-and-charge", action="store_true",
                        help="Boot 后不等远程启动，自己插枪并发 StartTransaction")
    parser.add_argument("--plug-after", type=float, default=2.0,
                        help="Boot 后多少秒插枪（即插即充模式）")
    parser.add_argument("--stay-plugged", action="store_true",
                        help="停止后报 SuspendedEV 而不是 Available，用于验收占位费")
    parser.add_argument("--unplug-after", type=int, default=0,
                        help="停止后多少秒拔枪（配合 --stay-plugged；0 表示一直插着）")
    parser.add_argument("--user", default="", help="OCPP Basic 用户名（服务端开启认证时用）")
    parser.add_argument("--password", default="", help="OCPP Basic 密码")
    args = parser.parse_args(argv)
    if not 1 <= args.connectors <= 4:
        parser.error("--connectors 必须在 1-4 之间")
    if args.power_kw is None:
        args.power_kw = 7.4 if args.mode == "ac" else 120.0
    if args.meter_step_wh is None:
        if args.mode == "ac":
            args.meter_step_wh = max(1, round(args.power_kw * 1000 * args.interval / 3600))
        else:
            args.meter_step_wh = 200
    if not 1 <= args.power_kw <= 1000:
        parser.error("--power-kw 必须在 1-1000 之间")
    if not 1 <= args.meter_step_wh <= 1000000:
        parser.error("--meter-step-wh 必须在 1-1000000 之间")
    if not 1 <= args.interval <= 60:
        parser.error("--interval 必须在 1-60 之间")
    if not 0 <= args.plug_after <= 3600:
        parser.error("--plug-after 必须在 0-3600 之间")
    if not 0 <= args.unplug_after <= 86400:
        parser.error("--unplug-after 必须在 0-86400 之间")
    return args


def main(argv=None):
    # Windows 控制台/重定向默认编码不是 UTF-8，日志要能被后端按 UTF-8 读回
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    args = parse_args(argv)
    point = ChargePoint(args)
    try:
        point.run()
    except KeyboardInterrupt:
        log("收到 Ctrl+C，退出")
        return 0
    except Exception as exc:  # noqa: BLE001 - 进程级兜底，日志要带原因
        log(f"退出：{exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
