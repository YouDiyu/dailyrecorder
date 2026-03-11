# 部署指南

## 目录
1. [环境准备](#环境准备)
2. [Supabase配置](#supabase配置)
3. [本地开发部署](#本地开发部署)
4. [生产环境部署](#生产环境部署)
5. [Android客户端集成](#android客户端集成)
6. [测试和验证](#测试和验证)
7. [常见问题](#常见问题)

---

## 环境准备

### 1. 系统要求
- Python 3.9+
- PostgreSQL 14+ (Supabase提供)
- Redis (可选，用于缓存)
- Node.js 16+ (用于Supabase CLI)

### 2. 安装依赖

```bash
# 创建虚拟环境
python -m venv venv

# 激活虚拟环境
# Windows
venv\Scripts\activate
# Linux/Mac
source venv/bin/activate

# 安装Python依赖
pip install -r requirements_backend.txt
```

---

## Supabase配置

### 1. 创建Supabase项目

1. 访问 [Supabase官网](https://supabase.com)
2. 注册/登录账号
3. 创建新项目
4. 记录以下信息：
   - Project URL: `https://xxxxx.supabase.co`
   - Anon/Public Key: `eyJhbGc...`
   - Service Role Key: `eyJhbGc...` (仅服务端使用)

### 2. 启用pgvector扩展

在Supabase Dashboard中：
1. 进入 `Database` → `Extensions`
2. 搜索 `vector`
3. 启用 `pgvector` 扩展

### 3. 执行数据库初始化脚本

在Supabase SQL Editor中执行 `supabase_setup.sql` 文件内容：

```sql
-- 复制supabase_setup.sql的全部内容并执行
```

### 4. 配置认证

在Supabase Dashboard中：
1. 进入 `Authentication` → `Providers`
2. 启用 Email 认证
3. 配置 Phone 认证（可选）
   - 需要配置SMS提供商（如Twilio）
4. 设置邮件模板（可选）

### 5. 配置Row Level Security (RLS)

RLS策略已在 `supabase_setup.sql` 中定义，确保已正确执行。

---

## 本地开发部署

### 1. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑.env文件，填入实际配置
```

`.env` 文件示例：
```env
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
OPENAI_API_KEY=sk-proj-xxxxx...
APP_ENV=development
DEBUG=True
HOST=0.0.0.0
PORT=8000
```

### 2. 启动开发服务器

```bash
# 方式1: 使用uvicorn直接运行
uvicorn backend_api:app --reload --host 0.0.0.0 --port 8000

# 方式2: 使用Python运行
python backend_api.py
```

### 3. 访问API文档

启动后访问：
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc
- 健康检查: http://localhost:8000/health

---

## 生产环境部署

### 方式1: 使用Docker部署

#### 1. 创建Dockerfile

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y \
    gcc \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# 复制依赖文件
COPY requirements_backend.txt .

# 安装Python依赖
RUN pip install --no-cache-dir -r requirements_backend.txt

# 复制应用代码
COPY backend_api.py .
COPY .env .

# 暴露端口
EXPOSE 8000

# 启动命令
CMD ["uvicorn", "backend_api:app", "--host", "0.0.0.0", "--port", "8000"]
```

#### 2. 构建和运行

```bash
# 构建镜像
docker build -t daily-recorder-api .

# 运行容器
docker run -d \
  --name daily-recorder \
  -p 8000:8000 \
  --env-file .env \
  daily-recorder-api
```

#### 3. 使用Docker Compose

创建 `docker-compose.yml`:

```yaml
version: '3.8'

services:
  api:
    build: .
    ports:
      - "8000:8000"
    env_file:
      - .env
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    restart: unless-stopped
```

运行：
```bash
docker-compose up -d
```

### 方式2: 云服务器部署

#### 1. 使用Nginx反向代理

安装Nginx:
```bash
sudo apt update
sudo apt install nginx
```

配置Nginx (`/etc/nginx/sites-available/daily-recorder`):
```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

启用配置:
```bash
sudo ln -s /etc/nginx/sites-available/daily-recorder /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

#### 2. 使用Systemd管理服务

创建服务文件 (`/etc/systemd/system/daily-recorder.service`):
```ini
[Unit]
Description=Daily Recorder API
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/var/www/daily-recorder
Environment="PATH=/var/www/daily-recorder/venv/bin"
ExecStart=/var/www/daily-recorder/venv/bin/uvicorn backend_api:app --host 0.0.0.0 --port 8000
Restart=always

[Install]
WantedBy=multi-user.target
```

启动服务:
```bash
sudo systemctl daemon-reload
sudo systemctl enable daily-recorder
sudo systemctl start daily-recorder
sudo systemctl status daily-recorder
```

#### 3. 配置HTTPS (Let's Encrypt)

```bash
# 安装Certbot
sudo apt install certbot python3-certbot-nginx

# 获取SSL证书
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

### 方式3: 云平台部署

#### Railway
1. 连接GitHub仓库
2. 添加环境变量
3. 自动部署

#### Render
1. 创建Web Service
2. 选择仓库
3. 配置环境变量
4. 部署

#### Heroku
```bash
# 安装Heroku CLI
heroku login

# 创建应用
heroku create daily-recorder-api

# 设置环境变量
heroku config:set SUPABASE_URL=xxx
heroku config:set SUPABASE_KEY=xxx
heroku config:set OPENAI_API_KEY=xxx

# 部署
git push heroku main
```

---

## Android客户端集成

### 1. 添加依赖

在 `build.gradle` 中添加：

```gradle
dependencies {
    // Retrofit for HTTP requests
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    
    // OkHttp for logging
    implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 2. 创建API接口

```kotlin
interface DailyRecorderApi {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
    
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @POST("api/records")
    suspend fun createRecord(
        @Header("Authorization") token: String,
        @Body request: RecordRequest
    ): Response<RecordResponse>
    
    @GET("api/records")
    suspend fun getRecords(
        @Header("Authorization") token: String,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 20
    ): Response<List<RecordResponse>>
    
    @POST("api/rag/summary")
    suspend fun getSummary(
        @Header("Authorization") token: String,
        @Body request: SummaryRequest
    ): Response<SummaryResponse>
}
```

### 3. 配置Retrofit

```kotlin
object ApiClient {
    private const val BASE_URL = "https://your-api-domain.com/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    val api: DailyRecorderApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DailyRecorderApi::class.java)
    }
}
```

### 4. 使用示例

```kotlin
class RecordRepository {
    private val api = ApiClient.api
    
    suspend fun createRecord(token: String, content: String): Result<RecordResponse> {
        return try {
            val response = api.createRecord(
                token = "Bearer $token",
                request = RecordRequest(content = content)
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to create record"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 测试和验证

### 1. API测试

使用curl测试：

```bash
# 健康检查
curl http://localhost:8000/health

# 用户注册
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test1234"
  }'

# 用户登录
curl -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test1234"
  }'

# 创建记录（需要替换TOKEN）
curl -X POST http://localhost:8000/api/records \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "content": "今天学习了Supabase和向量数据库"
  }'
```

### 2. 使用Postman测试

1. 导入API文档（从 `/docs` 导出OpenAPI规范）
2. 配置环境变量
3. 测试各个端点

### 3. 自动化测试

创建 `test_api.py`:

```python
import pytest
from fastapi.testclient import TestClient
from backend_api import app

client = TestClient(app)

def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "healthy"

def test_register():
    response = client.post(
        "/api/auth/register",
        json={
            "email": "test@example.com",
            "password": "Test1234"
        }
    )
    assert response.status_code in [201, 400]  # 400 if already exists
```

运行测试：
```bash
pytest test_api.py -v
```

---

## 常见问题

### 1. 向量化失败

**问题**: OpenAI API调用失败

**解决方案**:
- 检查API密钥是否正确
- 确认账户有足够余额
- 检查网络连接
- 考虑添加重试机制

### 2. 数据库连接问题

**问题**: 无法连接到Supabase

**解决方案**:
- 验证SUPABASE_URL和SUPABASE_KEY
- 检查网络防火墙设置
- 确认Supabase项目状态正常

### 3. RLS策略问题

**问题**: 用户无法访问自己的数据

**解决方案**:
- 检查RLS策略是否正确执行
- 验证JWT令牌中的user_id
- 在Supabase Dashboard中测试策略

### 4. 性能优化

**建议**:
- 启用Redis缓存
- 使用连接池
- 优化数据库索引
- 实施CDN加速
- 使用异步任务队列

### 5. 安全加固

**建议**:
- 限制CORS来源
- 实施速率限制
- 启用HTTPS
- 定期更新依赖
- 实施日志审计

---

## 监控和维护

### 1. 日志管理

```python
# 配置结构化日志
import logging
from pythonjsonlogger import jsonlogger

logHandler = logging.StreamHandler()
formatter = jsonlogger.JsonFormatter()
logHandler.setFormatter(formatter)
logger.addHandler(logHandler)
```

### 2. 性能监控

推荐工具：
- Sentry (错误追踪)
- Prometheus + Grafana (指标监控)
- New Relic (APM)

### 3. 备份策略

- Supabase自动备份
- 定期导出重要数据
- 测试恢复流程

---

## 支持和反馈

如有问题，请：
1. 查看日志文件
2. 检查Supabase Dashboard
3. 参考API文档
4. 提交Issue

---

**部署完成后，记得测试所有核心功能！**
