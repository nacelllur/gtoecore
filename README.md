# gtoecore — GregTech: Orbital Era 核心 Mod

**GregTech: Orbital Era** 整合包的专属内容 Mod。基于 **GregTech CEu Modern (GTCEu) 7.5.3** 的 Forge 1.20.1 addon。

- Mod ID：`gtoecore`
- Java 包：`com.gtoecore`
- 版本：`1.20.1-0.1.0`
- 构建产物：`gtoecore-1.20.1-0.1.0.jar`

> 说明：工程目录沿用了历史名 `gtn-core`，`build.gradle` 的 Maven `group` 也保留为 `com.gtncore`，二者均不影响 mod 的 modId 与运行时行为。

## 内容

### GT 多方块机器
- **蜂群之心** `drone_swarm_heart` — 无人机蜂群控制核心
- **克隆体系** — 克隆体维护室 / 克隆体制造仓 / 克隆体生产车间
- **深空枢纽** `deep_space_hub` — 模块化可扩展机器（配套 `DeepSpaceHubManager` / 模块配方修饰器）
- **太阳阵列** — 太空光伏发电
- **站重力核心** — 与 `space_gravity` 联动的失重区域控制

### 结构工具链
- `com.gtoecore.util.GTNPreview` — 多方块结构预览助手（状态方块 / 可选 mod 安全退化）
- `com.gtoecore.util.GTNStructureFixup` — 终端 autoBuild 摆出的装饰方块朝向修复
- `com.gtoecore.gt.GTNMachineOverrides` — 替换已有 GTM 机器的结构（已启用：合金高炉）

## 构建

### 环境要求
| 项目 | 版本 |
|---|---|
| JDK | 17 |
| Gradle | 8.8 |
| Minecraft | 1.20.1 |
| Forge | 47.x |
| GTCEu | 7.5.3 |

### 命令

```bash
export JAVA_HOME=/path/to/jdk-17

# 首次构建需要联网拉取 Forge/GTCEu 依赖；之后可加 --offline
gradle build
```

产物位于 `build/libs/gtoecore-1.20.1-0.1.0.jar`，复制到整合包实例的 `mods/` 目录即可。

> ⚠️ `libs/` 内的依赖 jar（GTCEu、LDLib、Registrate 等）为编译期引用，需自行从整合包实例复制过来；本仓库不包含这些第三方 jar。

## 目录结构

```
gtn-core/
├── build.gradle            # 构建脚本（archivesName = gtoecore-1.20.1）
├── settings.gradle
├── gradle.properties
├── libs/                   # 编译期依赖 jar（不入库，需自行放置）
├── src/main/java/com/gtoecore/
│   ├── GTNCore.java        # Mod 主类
│   ├── GTNBlocks.java      # 方块注册
│   ├── GTNItems.java       # 物品注册
│   ├── deepspace/          # 深空枢纽
│   ├── gt/                 # GT 机器与 addon 注册
│   ├── item/               # 自定义物品
│   ├── station/            # 站重力核心
│   └── util/               # 预览 / 结构修复工具
├── src/main/resources/
│   ├── META-INF/mods.toml
│   ├── assets/gtoecore/    # 材质与模型
│   └── data/               # 方块状态 / 配方数据
└── tools/                  # 资源生成脚本（Python）
```

## 相关仓库

- [`packcompanion`](https://github.com/nacelllur/packcompanion) — 整合包适配层（跨 mod 兼容 / mixin 修补）
- [`GregTech-Orbital-Era`](https://github.com/nacelllur/GregTech-Orbital-Era) — 整合包本体（配置 / mods / 资源）

## 致谢

- [GregTech CEu Modern](https://github.com/GregTechCEu/GregTech) — 核心科技 Mod
- [LDLib](https://github.com/Low-Drag-MC/LDLib) — GTM 前置
- [Registrate](https://github.com/Registrate-MC/Registrate) — 注册框架

## License

MIT