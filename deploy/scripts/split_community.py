#!/usr/bin/env python3
"""从双仓主仓裁出可公开的社区版快照。

主仓同时承载社区版（AGPL-3.0）与标准版（闭源）。本脚本按排除规则复制工作区，
再改写只对双仓成立的构建/文档入口，最后跑一遍边界断言，确认快照里没有
com.balancloud.platform、platform 页面、V500+ 迁移与 VITE_EDITION 分支，
也没有内部设计文档（docs/ 只留 PROTOCOL-SDK.md）或指向它们的悬空引用。

不读取 git 索引：模块拆分尚未提交时，工作区才是唯一事实来源。

用法：
    python deploy/scripts/split_community.py ../balancloud-community
    python deploy/scripts/split_community.py tmp/public-community --force --build
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys

PLATFORM_PATHS = [
    'backend/balan-platform',
    'frontend/src/views/platform',
    'frontend/src/api/platform',
    'frontend/src/views/error/edition.vue',
    'backend/balan-server/src/main/resources/application-standard.yml',
    'frontend/.env.standard',
]

SKIP_DIRS = {
    '.git', '.idea', '.vscode', '__pycache__',
    'tmp', '100charge', 'node_modules', 'target', 'dist', 'dist-community', 'backups', 'logs',
}

SKIP_FILES = {
    '.env', '.env.local', 'application-local.yml', 'regression-api-summary.txt',
}

# 内部设计与验收文档：公开快照只带设备接入需要的那份（docs/PROTOCOL-SDK.md）。
# 进度、验收数字与商业化决策属经营文档，不发；口径见 LICENSING.md 与 MVP-DEV-PLAN §56。
NON_PUBLIC_DOCS = [
    'docs/MVP-DEV-PLAN.md',
    'docs/MINIPROGRAM-PLAN.md',
    'docs/AI-ROADMAP.md',
]

# 允许出现内部文档名与竞品目录名的文件：本脚本本身就是排除规则，.gitignore 是排除声明
REF_ALLOWLIST = {'deploy/scripts/split_community.py', '.gitignore'}
BINARY_SUFFIXES = ('.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.jar', '.zip',
                   '.woff', '.woff2', '.ttf', '.eot', '.pdf', '.mp4')


def skip_entry(directory, names):
    ignored = []
    for name in names:
        if name in SKIP_DIRS or name in SKIP_FILES:
            ignored.append(name)
        elif name.endswith(('.log', '.pem')):
            ignored.append(name)
    return ignored


def sub_once(path, pattern, replacement, flags=0):
    text = path.read_text(encoding='utf-8')
    new, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f'[split] 改写失败，未匹配到目标片段：{path}\n'
                         f'        pattern={pattern[:120]!r}（上游结构变了就同步改本脚本）')
    path.write_text(new, encoding='utf-8')


def replace_exact(path, old, new):
    text = path.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'[split] 改写失败，未找到预期文本：{path}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


def patch_maven(target):
    root_pom = target / 'backend/pom.xml'
    replace_exact(root_pom,
                  '      模块边界（docs/MVP-DEV-PLAN.md §28.2 / §31.3）：\n',
                  '      模块边界（授权与版本分层见 LICENSING.md）：\n')
    sub_once(root_pom, r'\n\s*<module>balan-platform</module>', '')
    replace_exact(root_pom, (
        '        balan-platform   闭源标准版 jar：com.balancloud.platform.*，迁移 V500+、菜单 900+；core 不得 import 本模块\n'
    ), '')
    replace_exact(root_pom, (
        '        balan-server     启动壳：community（默认，不含 platform）/ standard（含 platform）两个 Maven profile\n'
    ), '        balan-server     启动壳：只含社区版 core\n')
    sub_once(root_pom, (
        r'\n\s*<dependency>\s*<groupId>com\.balancloud</groupId>\s*'
        r'<artifactId>balan-platform</artifactId>\s*'
        r'<version>\$\{project\.version\}</version>\s*</dependency>'
    ), '')
    replace_exact(root_pom,
                  '<description>衡电云控 BALANCLOUD 充电桩运营平台</description>',
                  '<description>衡电云控 BALANCLOUD 充电桩运营平台（社区版）</description>')

    server_pom = target / 'backend/balan-server/pom.xml'
    replace_exact(server_pom,
                  '<description>启动壳：community（纯开源）/ standard（含标准版 jar）两个 Maven profile</description>',
                  '<description>启动壳：社区版（AGPL-3.0，纯开源 core）</description>')
    sub_once(server_pom, r'\n    <profiles>.*?</profiles>',
             '\n    <properties>\n        <edition.suffix>community</edition.suffix>\n    </properties>',
             flags=re.S)


def patch_frontend(target):
    permission = target / 'frontend/src/store/modules/permission.js'
    replace_exact(permission, (
        '// 匹配views里面所有的.vue文件\n'
        '// 版本裁剪：社区版不注册 views/platform/**。VITE_EDITION 会被静态替换成字面量，\n'
        '// 因此 false 分支连同 platformViews 的 glob 一起被 Rollup 判为死代码（是否真的不产 chunk 由构建验证）。\n'
        "const coreViews = import.meta.glob(['./../../views/**/*.vue', '!./../../views/platform/**'])\n"
        "const platformViews = import.meta.glob('./../../views/platform/**/*.vue')\n"
        "const modules = import.meta.env.VITE_EDITION === 'standard' ? { ...coreViews, ...platformViews } : coreViews\n"
    ), (
        '// 匹配views里面所有的.vue文件（社区版仓库内只有核心页面）\n'
        "const modules = import.meta.glob('./../../views/**/*.vue')\n"
    ))

    router = target / 'frontend/src/router/index.js'
    sub_once(router, r'\n// 标准版专属菜单的顶级路径[\s\S]*?\n\}\n', '\n')

    pkg = target / 'frontend/package.json'
    text = pkg.read_text(encoding='utf-8')
    data = json.loads(text)
    data['scripts'].pop('build:standard', None)
    data['license'] = 'AGPL-3.0-only'
    pkg.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def patch_config(target):
    app_yml = target / 'backend/balan-server/src/main/resources/application.yml'
    replace_exact(app_yml,
                  'MQTT_CLIENT_ID:balancloud-platform',
                  'MQTT_CLIENT_ID:balancloud-community')
    replace_exact(app_yml,
                  '    # core 迁移只用 ≤V99 段；标准版 jar 的 V500+ 段由 standard profile 追加（见 application-standard.yml）\n',
                  '    # 社区版迁移只用 ≤V99 段，菜单 1–899\n')
    local_example = target / 'backend/balan-server/src/main/resources/application-local.yml.example'
    sub_once(local_example, r'\n#   payment:\n(?:#     .*\n)+', '\n')


def patch_docs(target, templates):
    """根 README 是公开快照的唯一来源；这里只改掉指向内部文档的引用。"""
    readme = target / 'README.md'
    replace_exact(readme, (
        '- 文档：[docs/MVP-DEV-PLAN.md](docs/MVP-DEV-PLAN.md)（规格与验收记录）、'
        '[docs/PROTOCOL-SDK.md](docs/PROTOCOL-SDK.md)（设备接入）、'
        '[docs/MINIPROGRAM-PLAN.md](docs/MINIPROGRAM-PLAN.md)（小程序）、'
        '[CONTRIBUTING.md](CONTRIBUTING.md)'
    ), (
        '- 文档：[docs/PROTOCOL-SDK.md](docs/PROTOCOL-SDK.md)（设备接入）、[CONTRIBUTING.md](CONTRIBUTING.md)\n'
        '- 规格与验收记录、小程序设计稿、AI 路线规划是内部文档，不随本仓库分发；功能与接口以代码为准。'
    ))
    replace_exact(readme,
                  '完整清单见 [docs/MVP-DEV-PLAN.md §9](docs/MVP-DEV-PLAN.md)。',
                  '接口清单以代码为准：`backend/balan-core` 下的 controller 与 `frontend/src/api`。')
    replace_exact(readme,
                  '- AI 诊断不在本仓库，规划与边界见 [docs/AI-ROADMAP.md](docs/AI-ROADMAP.md)：'
                  '平台只负责把设备、订单、告警与事件流跑通。',
                  '- AI 诊断不在本仓库，也还没有对外文档：平台只负责把设备、订单、告警与事件流跑通。')
    # edition.vue 属标准版专属文件，快照里没有这个路径
    replace_exact(readme,
                  '前端落到 [views/error/edition.vue](frontend/src/views/error/edition.vue) 的「标准版功能」说明页。',
                  '前端落到「标准版功能」说明页。')

    ci = target / '.github/workflows/ci.yml'
    shutil.copyfile(templates / 'ci.yml', ci)

    licensing = target / 'LICENSING.md'
    replace_exact(licensing,
                  '双许可模式。同一个代码仓库里同时存放两套边界清晰的代码，按目录划分授权范围。',
                  '双许可模式。本仓库是社区版：下表中列出的标准版内容不在本仓库内，'
                  '也不属于 AGPL-3.0 的授权范围。')
    replace_exact(licensing,
                  '| 内部设计、验收与商业化口径，只在主仓留存，**不随社区版快照分发** |',
                  '| 内部设计、验收与商业化口径，不随本仓库分发 |')

    notice = target / 'NOTICE'
    replace_exact(notice,
                  'This repository contains two editions with different licenses:',
                  'This repository is the community edition of BALANCLOUD:')
    replace_exact(notice, (
        '    (versions V500 and above), and the internal design and acceptance\n'
        '    documents under docs/ (everything except docs/PROTOCOL-SDK.md),\n'
        '    which are not distributed with any public repository.\n'
    ), (
        '    (versions V500 and above). The internal design and acceptance\n'
        '    documents under docs/ are not distributed here either; this\n'
        '    repository ships docs/PROTOCOL-SDK.md and nothing else from docs/.\n'
    ))
    replace_exact(notice,
                  '  Schema and migration scripts (core Flyway V1–V30, platform Flyway\n'
                  '  V500–V524) are original work.',
                  '  Schema and migration scripts (core Flyway V1–V30) are original work.')

    contributing = target / 'CONTRIBUTING.md'
    replace_exact(contributing,
                  '5. 至少保证 `mvn test` 通过；涉及社区版边界的改动，还要确认 `mvn package -Pcommunity` 与 `mvn package -Pstandard` 都能构建。',
                  '5. 至少保证 `mvn test` 通过，并且 `mvn package` 能构建。')


def patch_core_comments(target):
    """社区版里没有 platform 包，指向它的注释改成中性说法，避免读者去找不存在的文件。"""
    app = target / 'backend/balan-server/src/main/java/com/balancloud/BalanCloudApplication.java'
    replace_exact(app,
                  '// 通配扫描：闭源 jar（com.balancloud.platform.*）带自己的 mapper 时不需要改这里',
                  '// 通配扫描：com.balancloud 下的 controller / mapper 自动注册')

    grant = target / 'backend/balan-core/src/test/java/com/balancloud/system/service/SysRoleGrantTest.java'
    replace_exact(grant,
                  ' * 三条菜单查询在同空同非空上的表现依赖真实库，见 docs/MVP-DEV-PLAN.md §26.4 的验收脚本。\n',
                  ' * 三条菜单查询在同空同非空上的表现依赖真实库，需起服务后用接口回归验证。\n')

    checker = target / 'backend/balan-core/src/main/java/com/balancloud/framework/config/EditionBoundaryChecker.java'
    text = checker.read_text(encoding='utf-8')
    text = text.replace('与 balan-platform 各迁移里的 menu_id 约定一致', '与标准版迁移里的 menu_id 约定一致')
    text = text.replace('与 application-standard.yml 追加的 db/migration/platform 一致', '与标准版启动参数追加的迁移段一致')
    text = text.replace('（V{}+ 属 balan-platform）', '（V{}+ 属标准版）')
    checker.write_text(text, encoding='utf-8')


def patch_local_example(target):
    """本地示例里标准版专属配置随模块一起删除。"""
    path = target / 'backend/balan-server/src/main/resources/application-local.yml.example'
    if path.exists():
        text = path.read_text(encoding='utf-8')
        text = text.replace(
            '  payment:\n'
            '    # 标准版沙箱支付回调签名密钥，仅本地使用；生产用 PAYMENT_CALLBACK_SECRET 注入\n'
            '    callback-secret: your_callback_hmac_secret\n', '')
        path.write_text(text, encoding='utf-8')


def prune_docs_images(target):
    """docs 下的图片只留公开文档真正引用到的那些。

    主仓 docs/screenshots 有 100 张，多数是内部验收留证：里面既有上游 RuoYi 的
    真人默认头像，也有改版前的登录页文案。不发出去的东西不该躺在公开快照里，
    按引用裁剪比逐个拉黑名单可靠（verify 会再查一遍引用是否还能命中）。
    """
    refs = set()
    pattern = re.compile(r'docs/([A-Za-z0-9._/-]+\.(?:png|jpg|jpeg|webp|gif|svg))')
    for path in target.rglob('*.md'):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        refs.update('docs/' + name
                    for name in pattern.findall(path.read_text(encoding='utf-8', errors='ignore')))

    kept = removed = 0
    for directory in ('docs', 'docs/screenshots'):
        base = target / directory
        if not base.is_dir():
            continue
        for shot in sorted(base.iterdir()):
            rel = shot.relative_to(target).as_posix()
            if rel in refs:
                kept += 1
            elif shot.suffix.lower() in ('.png', '.jpg', '.jpeg', '.webp', '.gif', '.svg'):
                shot.unlink()
                removed += 1
    print(f'[split] docs 图片：保留 {kept} 张（公开文档引用），裁掉 {removed} 张未引用留证')
    if kept == 0:
        raise SystemExit('[split] docs 图片全被裁掉了，README 的图引用八成没匹配上')


def iter_text_files(target):
    for directory, dirs, files in os.walk(target):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            path = os.path.join(directory, name)
            if name.endswith(BINARY_SUFFIXES) or not os.path.isfile(path):
                continue
            if os.path.getsize(path) > 2 * 1024 * 1024:
                continue
            yield path


def verify(target):
    problems = []

    for rel in PLATFORM_PATHS:
        if (target / rel).exists():
            problems.append(f'仍存在：{rel}')

    for rel in NON_PUBLIC_DOCS:
        if (target / rel).exists():
            problems.append(f'内部文档未排除：{rel}')

    # 不发文档就不许被引用；本脚本与 .gitignore 是排除规则的载体，自身除外
    dangling = tuple(NON_PUBLIC_DOCS + ['100charge'])
    for path in iter_text_files(target):
        rel = os.path.relpath(path, target).replace(os.sep, '/')
        if rel in REF_ALLOWLIST:
            continue
        try:
            text = open(path, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        for token in dangling:
            if token in text:
                problems.append(f'{rel} 仍引用不随本仓库分发的 {token}')
        if '待拍板' in text:
            problems.append(f'{rel} 含未定决策字样「待拍板」')
        if rel.endswith('.md'):
            for ref in re.findall(r'\[[^\]]*\]\(([^)#\s]+)\)', text):
                if ref.startswith(('http', 'mailto:', '#')):
                    continue
                if not os.path.exists(os.path.join(os.path.dirname(path), ref)):
                    problems.append(f'{rel} 的链接指向不存在的文件：{ref}')

    scanned = []
    for base in ('backend', 'frontend/src', 'miniapp/src', '.github'):
        for directory, dirs, files in os.walk(target / base):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for name in files:
                if name.endswith(('.java', '.xml', '.yml', '.yaml', '.json', '.js', '.vue', '.sql')):
                    scanned.append(os.path.join(directory, name))

    forbidden = ('com.balancloud.platform', 'views/platform', 'api/platform', 'balan-platform')
    for path in scanned:
        try:
            text = open(path, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        for token in forbidden:
            if token in text:
                problems.append(f'{os.path.relpath(path, target)} 仍引用 {token}')

    migrations = sorted((target / 'backend/balan-core/src/main/resources/db/migration/core').glob('*.sql'))
    for migration in migrations:
        match = re.match(r'V(\d+)__', migration.name)
        if match and int(match.group(1)) >= 500:
            problems.append(f'core 迁移越过社区版段界：{migration.name}')
    if not migrations:
        problems.append('core 迁移目录为空')

    if problems:
        print('[split] 边界校验失败：', file=sys.stderr)
        for item in problems:
            print('  - ' + item, file=sys.stderr)
        raise SystemExit(1)

    java_count = sum(1 for p in scanned if p.endswith('.java'))
    total = sum(len(files) for _, _, files in os.walk(target) if '.git' not in _)
    print(f'[split] 边界校验通过：{java_count} 个 Java 文件，{len(migrations)} 个 core 迁移，共 {total} 个文件')


def read_version(target):
    pom = (target / 'backend/pom.xml').read_text(encoding='utf-8')
    match = re.search(r'<artifactId>balancloud-backend</artifactId>\s*<version>([^<]+)</version>', pom)
    if not match:
        return '0.0.0'
    # 保留 -SNAPSHOT：模板里的 jar 名必须能和构建产物对上
    return match.group(1)


def build(target):
    java_home = os.environ.get('JAVA_HOME', '')
    if not java_home:
        print('[split] 提示：未设置 JAVA_HOME，若本机 Maven 默认不是 JDK 17 请先设置。')
    print('[split] 构建社区版后端 ...')
    mvn = shutil.which('mvn') or shutil.which('mvn.cmd')
    if not mvn:
        raise SystemExit('[split] 找不到 mvn，请确认 Maven 已在 PATH 中')
    result = subprocess.run(
        [mvn, '-B', '-ntp', '-q', 'package', '-DskipTests'],
        cwd=target / 'backend',
    )
    if result.returncode != 0:
        raise SystemExit('[split] 社区版构建失败')
    jar = next((target / 'backend/balan-server/target').glob('balancloud-*-community.jar'), None)
    if not jar:
        raise SystemExit('[split] 构建结束但没找到 community jar')
    print(f'[split] 构建通过：{jar.relative_to(target)}')


def main():
    parser = argparse.ArgumentParser(description='裁出 BALANCLOUD 社区版快照')
    parser.add_argument('target', help='输出目录，例如 ../balancloud-community')
    parser.add_argument('--force', action='store_true', help='目标目录非空时先删除')
    parser.add_argument('--build', action='store_true', help='复制后立即构建社区版后端')
    args = parser.parse_args()

    root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
    target = os.path.abspath(args.target)
    if os.path.commonpath([root, target]) == target:
        raise SystemExit(f'[split] 目标目录不能是主仓或其父目录：{target}')

    if os.path.exists(target):
        if not args.force:
            if os.listdir(target):
                raise SystemExit(f'[split] 目标目录非空：{target}（确认要覆盖请加 --force）')
        else:
            shutil.rmtree(target)
    os.makedirs(target, exist_ok=True)

    print(f'[split] 主仓：{root}')
    print(f'[split] 输出：{target}')
    shutil.copytree(root, target, dirs_exist_ok=True, ignore=skip_entry)

    from pathlib import Path
    out = Path(target).resolve()
    templates = Path(root).resolve() / 'deploy/templates/community'
    for rel in PLATFORM_PATHS:
        path = out / rel
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()
    # 模板只留给主仓的构建脚本使用，不进公开快照
    shutil.rmtree(out / 'deploy/templates', ignore_errors=True)
    for rel in NON_PUBLIC_DOCS:
        path = out / rel
        if path.exists():
            path.unlink()

    patch_maven(out)
    patch_frontend(out)
    patch_config(out)
    patch_core_comments(out)
    patch_local_example(out)
    version = read_version(out)
    patch_docs(out, templates)
    prune_docs_images(out)
    verify(out)

    print(f'[split] 社区版快照就绪（版本 {version}）')
    if args.build:
        build(out)


if __name__ == '__main__':
    main()
