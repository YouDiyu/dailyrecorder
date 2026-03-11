# Daily Recorder - Android应用后端系统

基于Supabase增强的Android应用后端系统，实现用户认证、日常记录管理和RAG知识库功能。

## 🌟 核心特性

### 1. 安全的用户认证体系
- ✅ 邮箱/手机号注册登录
- ✅ JWT令牌管理（访问令牌 + 刷新令牌）
- ✅ 密码加密存储（bcrypt）
- ✅ 会话管理和令牌刷新

### 2. 日常记录管理
- ✅ 文本记录上传
- ✅ 用户ID关联索引
- ✅ CRUD完整操作
- ✅ 自动时间戳管理

### 3. RAG知识库系统
- ✅ 自动向量化处理（OpenAI text-embedding-ada-002）
- ✅ pgvector向量存储
- ✅ 语义相似度搜索
- ✅ 个性化每日总结生成
- ✅ 实时调用向量库

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    Android客户端                         │
│              (Retrofit + Kotlin Coroutines)             │
└────────────────────┬────────────────────────────────────┘
                     │ HTTPS/REST API
                     ▼
┌─────────────────────────────────────────────────────────┐
│                  FastAPI后端服务                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  认证模块    │  │  记录管理    │  │  RAG模块     │  │
│  │  JWT/OAuth   │  │  CRUD API    │  │  向量搜索    │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
┌──────────────────┐    ┌──────────────────┐
│   Supabase       │    │   OpenAI API     │
│  ┌────────────┐  │    │  ┌────────────┐  │
│  │ PostgreSQL │  │    │  │ Embedding  │  │
│  │ + pgvector │  │    │  │   Model    │  │
│  └────────────┘  │    │  └────────────┘  │
│  ┌────────────┐  │    │  ┌────────────┐  │
│  │    Auth    │  │    │  │    GPT     │  │
│  │   (JWT)    │  │    │  │   Model    │  │
│  └────────────┘  │    │  └────────────┘  │
└──────────────────┘    └──────────────────┘
```

## 📁 项目结构

```
dailyrecorder/
├── ARCHITECTURE.md           # 系统架构设计文档
├── DEPLOYMENT_GUIDE.md       # 详细部署指南
├── README.md                 # 项目说明文档
├── backend_api.py            # FastAPI后端主程序
├── supabase_setup.sql        # 数据库初始化脚本
├── requirements_backend.txt  # Python依赖列表
├── .env.example              # 环境变量模板
└── tests/                    # 测试文件（待创建）
```

## 🚀 快速开始

### 前置要求

- Python 3.9+
- Supabase账号
- OpenAI API密钥

### 1. 克隆项目

```bash
git clone <repository-url>
cd dailyrecorder
```

### 2. 安装依赖

```bash
# 创建虚拟环境
python -m venv venv

# 激活虚拟环境
# Windows
venv\Scripts\activate
# Linux/Mac
source venv/bin/activate

# 安装依赖
pip install -r requirements_backend.txt
```

### 3. 配置Supabase

1. 访问 [Supabase](https://supabase.com) 创建项目
2. 启用 `pgvector` 扩展
3. 在SQL Editor中执行 `supabase_setup.sql`
4. 配置认证提供商（Email/Phone）

### 4. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑.env文件，填入配置
# SUPABASE_URL=https://xxxxx.supabase.co
# SUPABASE_KEY=your-anon-key
# OPENAI_API_KEY=sk-your-key
```

### 5. 启动服务

```bash
# 开发模式
uvicorn backend_api:app --reload --host 0.0.0.0 --port 8000

# 或直接运行
python backend_api.py
```

### 6. 访问API文档

- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc
- 健康检查: http://localhost:8000/health

## 📚 API端点

### 认证相关

| 方法 | 端点 | 描述 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/refresh` | 刷新令牌 |
| POST | `/api/auth/logout` | 用户登出 |
| GET  | `/api/auth/profile` | 获取用户信息 |

### 记录管理

| 方法 | 端点 | 描述 |
|------|------|------|
| POST   | `/api/records` | 创建记录 |
| GET    | `/api/records` | 获取所有记录 |
| GET    | `/api/records/{id}` | 获取单条记录 |
| PUT    | `/api/records/{id}` | 更新记录 |
| DELETE | `/api/records/{id}` | 删除记录 |

### RAG知识库

| 方法 | 端点 | 描述 |
|------|------|------|
| POST | `/api/rag/search` | 语义搜索 |
| POST | `/api/rag/summary` | 生成每日总结 |
| GET  | `/api/rag/insights` | 获取个性化洞察 |

## 🔒 安全特性

- **JWT认证**: 访问令牌15分钟，刷新令牌7天
- **密码加密**: bcrypt加密，cost factor 12
- **RLS策略**: 用户只能访问自己的数据
- **输入验证**: Pydantic模型验证
- **HTTPS强制**: 生产环境必须使用HTTPS
- **CORS配置**: 可配置允许的来源

## 📊 数据库设计

### daily_records 表
```sql
- id (uuid, primary key)
- user_id (uuid, foreign key)
- content (text)
- created_at (timestamp)
- updated_at (timestamp)
- metadata (jsonb)
```

### record_embeddings 表
```sql
- id (uuid, primary key)
- record_id (uuid, foreign key)
- user_id (uuid, foreign key)
- embedding (vector(1536))
- created_at (timestamp)
```

## 🔄 向量化流程

```
用户上传文本 → 保存到数据库 → 异步生成向量 → 存储到pgvector
                                    ↓
                            OpenAI Embedding API
                            (text-embedding-ada-002)
```

## 🤖 RAG检索流程

```
用户请求总结 → 生成查询向量 → pgvector相似度搜索 → 检索相关记录
                                                    ↓
                                            构建上下文prompt
                                                    ↓
                                            调用GPT生成总结
                                                    ↓
                                            返回个性化总结
```

## 🧪 测试

```bash
# 运行测试
pytest tests/ -v

# 测试覆盖率
pytest --cov=backend_api tests/

# API测试示例
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "Test1234"}'
```

## 📦 部署

详细部署指南请参考 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)

### Docker部署

```bash
# 构建镜像
docker build -t daily-recorder-api .

# 运行容器
docker run -d -p 8000:8000 --env-file .env daily-recorder-api
```

### Docker Compose

```bash
docker-compose up -d
```

## 🔧 配置选项

主要环境变量：

```env
# Supabase
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_KEY=your-anon-key

# OpenAI
OPENAI_API_KEY=sk-your-key

# 应用
APP_ENV=production
DEBUG=False
HOST=0.0.0.0
PORT=8000

# 向量化
EMBEDDING_MODEL=text-embedding-ada-002
VECTOR_SEARCH_THRESHOLD=0.7
```

## 📱 Android客户端集成

### Retrofit配置示例

```kotlin
interface DailyRecorderApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @POST("api/records")
    suspend fun createRecord(
        @Header("Authorization") token: String,
        @Body request: RecordRequest
    ): Response<RecordResponse>
}
```

详细集成指南请参考 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#android客户端集成)

## 🐛 常见问题

### 1. 向量化失败
- 检查OpenAI API密钥
- 确认账户余额
- 检查网络连接

### 2. 数据库连接问题
- 验证Supabase配置
- 检查防火墙设置
- 确认项目状态

### 3. 认证失败
- 检查JWT令牌格式
- 验证令牌有效期
- 确认RLS策略

更多问题请参考 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#常见问题)

## 📈 性能优化

- **数据库索引**: 已优化用户ID和时间戳索引
- **向量索引**: IVFFlat算法加速相似度搜索
- **异步处理**: 向量化采用后台任务
- **连接池**: 数据库连接池管理
- **缓存**: 可选Redis缓存支持

## 🛣️ 路线图

- [ ] 支持多模态记录（图片、音频）
- [ ] 实现协作功能
- [ ] 添加数据导出功能
- [ ] 高级分析和可视化
- [ ] 移动端离线支持
- [ ] 多语言支持

## 📄 许可证

[MIT License](LICENSE)

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📞 联系方式

如有问题或建议，请通过以下方式联系：
- 提交Issue
- 发送邮件

## 🙏 致谢

- [Supabase](https://supabase.com) - 开源Firebase替代方案
- [FastAPI](https://fastapi.tiangolo.com) - 现代Python Web框架
- [OpenAI](https://openai.com) - AI模型和API
- [pgvector](https://github.com/pgvector/pgvector) - PostgreSQL向量扩展

---

**开始构建你的智能日常记录应用吧！** 🚀
