# SmartLab 2.0 工作空间规范与指令 (AGENTS.md)

本文件为 OpenAI Codex / 智能助手在此项目中的全局工作区指引。

## 1. 项目结构与环境配置

* **后端环境 (Backend)**
  * **JDK 路径**：`D:\java\JDK21` (Java 21)
  * **Maven 路径**：`D:\Maven\apache-maven-3.9.9\bin\mvn.cmd`
  * **Maven 配置文件**：`D:\Maven\apache-maven-3.9.9\conf\settings.xml`
  * **用户设置**：`C:\Users\22960\.m2\settings.xml`

* **前端环境 (Frontend)**
  * **框架**：Vue 3 + Vite + TypeScript / JavaScript
  * **目录**：`Frontend`
  * **模块热重载与编译**：使用现有的 Node.js / npm 脚本进行启动与构建。

## 2. 编码与改动准则
* 在进行后端修改时，保持与现有 Spring Boot / 接口规范一致。
* 在进行前端修改时，修改对应组件并确保状态管理与页面响应式逻辑正确。
* 修改代码前优先验证上下文，修改后执行必要的编译与单元测试。
