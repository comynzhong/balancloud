#!/usr/bin/env python3
"""把社区版快照发布到本地独立 git 仓（默认不 push）。

主仓同时含闭源模块，绝不能直接推公共远端；发布走 split_community.py 裁出的快照目录，
它和主仓的 .git 没有任何关系，历史从这里重新开始，每次发布追加一条提交。

快照先裁到 <发布目录>.staging 再倒进去：split 的 --force 会整目录 rmtree，
直接对发布目录跑会连它自己的提交历史一起删掉。

用法：
    python deploy/scripts/publish_community.py                  # 裁剪 + 本地提交
    python deploy/scripts/publish_community.py --build          # 顺带构建社区版 jar 验证可编译
    python deploy/scripts/publish_community.py --push           # 确认内容后推到 origin
    python deploy/scripts/publish_community.py --target D:/x    # 换发布目录（默认 ../balancloud-community）
"""

import argparse
import datetime
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# 提交前拦一道：快照里不该出现的文件（split 脚本已在复制阶段排除，这里是防回归）。
# 按精确文件名匹配：deploy/.env.example 与 application-local.yml.example 是故意公开的
# 占位模板，用子串匹配会把它们误伤成泄漏。
FORBIDDEN_BASENAMES = ('.env', 'application-local.yml', '.env.standard')
FORBIDDEN_KEYWORDS = ('balan-platform',)
FORBIDDEN_SUFFIXES = ('.pem', '.key', '.p12', '.jks')


def run(cmd, cwd=None, capture=False):
    result = subprocess.run(cmd, cwd=cwd, text=True, capture_output=capture, encoding='utf-8')
    if capture and result.returncode != 0:
        raise SystemExit(f'[publish] 命令失败：{" ".join(cmd)}\n{result.stdout}{result.stderr}')
    if not capture and result.returncode != 0:
        raise SystemExit(f'[publish] 命令失败：{" ".join(cmd)}')
    return (result.stdout or '').strip()


def _forbidden(path):
    normalized = path.replace('\\', '/')
    basename = normalized.rsplit('/', 1)[-1]
    return (basename in FORBIDDEN_BASENAMES
            or any(keyword in normalized for keyword in FORBIDDEN_KEYWORDS)
            or normalized.endswith(FORBIDDEN_SUFFIXES))


def _swap(target, staging):
    """暂存目录的内容覆盖到发布目录，只保留发布目录自己的 .git。"""
    for entry in target.iterdir():
        if entry.name == '.git':
            continue
        shutil.rmtree(entry) if entry.is_dir() else entry.unlink()
    shutil.copytree(staging, target, dirs_exist_ok=True)
    shutil.rmtree(staging)


def main():
    parser = argparse.ArgumentParser(description='发布 BALANCLOUD 社区版快照')
    parser.add_argument('--target', default=str(ROOT.parent / 'balancloud-community'))
    parser.add_argument('--build', action='store_true', help='裁剪后构建社区版后端')
    parser.add_argument('--push', action='store_true', help='提交后推送到 origin')
    parser.add_argument('--message', default='')
    args = parser.parse_args()

    target = Path(os.path.abspath(args.target))
    if target == ROOT or ROOT in target.parents:
        raise SystemExit('[publish] 发布目录不能放在主仓里面，否则会把闭源仓的嵌套仓带进历史')

    # 先裁到旁边的暂存目录，再倒进发布目录：split 的 --force 会整目录 rmtree，
    # 直接对发布目录跑会把上一次社区版的提交历史一起删掉。
    staging = target.parent / (target.name + '.staging')
    split = [sys.executable, str(ROOT / 'deploy' / 'scripts' / 'split_community.py'), str(staging), '--force']
    if args.build:
        split.append('--build')
    run(split)

    source_sha = run(['git', 'rev-parse', '--short', 'HEAD'], cwd=ROOT, capture=True)
    target.mkdir(parents=True, exist_ok=True)
    if not (target / '.git').exists():
        run(['git', 'init', '-b', 'main'], cwd=target, capture=True)

    _swap(target, staging)

    run(['git', 'add', '-A'], cwd=target, capture=True)
    staged = run(['git', 'ls-files'], cwd=target, capture=True).splitlines()
    leaked = [p for p in staged if _forbidden(p)]
    if leaked:
        raise SystemExit('[publish] 快照里出现不该公开的文件，已中止：\n  ' + '\n  '.join(leaked))
    print(f'[publish] 待提交 {len(staged)} 个文件，敏感路径检查通过')

    changed = run(['git', 'status', '--porcelain'], cwd=target, capture=True)
    if not changed:
        print('[publish] 快照内容与上次提交完全一致，无需发布')
        return

    message = args.message or (
        f'【发布】社区版快照 {datetime.date.today().isoformat()}（源自主仓 {source_sha}）\n\n'
        '由 deploy/scripts/publish_community.py 生成，仅含 AGPL-3.0 的社区版代码。'
    )
    run(['git', 'commit', '-m', message], cwd=target, capture=True)
    head = run(['git', 'log', '--oneline', '-1'], cwd=target, capture=True)
    print(f'[publish] 已本地提交：{head}')

    remotes = run(['git', 'remote'], cwd=target, capture=True)
    if not args.push:
        print('[publish] 未推送。先自查：')
        print(f'    cd "{target}" && git log --oneline && git ls-files | head')
        print('    确认无误后加 --push（需要先有一次 git remote add origin <公共仓地址>）')
        return
    if 'origin' not in remotes.splitlines():
        raise SystemExit('[publish] 发布目录还没有 origin 远端，先执行 git remote add origin <地址>')
    run(['git', 'push', '-u', 'origin', 'main'], cwd=target)
    print('[publish] 已推送到 origin/main')


if __name__ == '__main__':
    main()
