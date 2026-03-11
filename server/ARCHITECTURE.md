# Android应用后端系统架构设计

## 系统概述
基于Supabase构建的Android应用后端系统，实现用户认证、日常记录管理和RAG知识库功能。

## 技术栈
- **后端框架**: Python Flask/FastAPI
- **数据库**: Supabase (PostgreSQL + pgvector扩展)
- **认证**: Supabase Auth (JWT)
- **向量化**: OpenAI text-embedding-ada-002
- **部署**: 支持云服务器部署

## 核心功能模块

### 1. 用户认证系统
#### 功能特性
- 邮箱/手机号注册登录
- JWT令牌管理（访问令牌 + 刷新令牌）
- 密码加密存储（bcrypt）
- 会话管理

#### API端点
```
POST /api/auth/register          # 用户注册
POST /api/auth/login             # 用户登录
POST /api/auth/refresh           # 刷新令牌
POST /api/auth/logout            # 用户登出
GET  /api/auth/profile           # 获取用户信息
```

### 2. 日常记录管理
#### 功能特性
- 文本记录上传
- 用户ID关联索引
- 记录查询和管理
- 自动时间戳

#### API端点
```
POST   /api/records              # 创建记录
GET    /api/records              # 获取用户所有记录
GET    /api/records/{id}         # 获取单条记录
PUT    /api/records/{id}         # 更新记录
DELETE /api/records/{id}         # 删除记录
```

### 3. RAG知识库系统
#### 功能特性
- 自动向量化处理
- pgvector存储
- 语义相似度搜索
- 个性化总结生成

#### API端点
```
POST /api/rag/search             # 语义搜索
POST /api/rag/summary            # 生成每日总结
GET  /api/rag/insights           # 获取个性化洞察
```

## 数据库设计

### 表结构

#### users表（由Supabase Auth管理）
```sql
- id (uuid, primary key)
- email (text, unique)
- phone (text, unique)
- encrypted_password (text)
- created_at (timestamp)
- updated_at (timestamp)
```

#### daily_records表
```sql
- id (uuid, primary key)
- user_id (uuid, foreign key -> auth.users)
- content (text)
- created_at (timestamp)
- updated_at (timestamp)
- metadata (jsonb)
```

#### record_embeddings表
```sql
- id (uuid, primary key)
- record_id (uuid, foreign key -> daily_records)
- user_id (uuid, foreign key -> auth.users)
- embedding (vector(1536))  # OpenAI ada-002维度
- created_at (timestamp)
```

### 索引设计
```sql
-- 用户记录索引
CREATE INDEX idx_records_user_id ON daily_records(user_id);
CREATE INDEX idx_records_created_at ON daily_records(created_at DESC);

-- 向量相似度索引
CREATE INDEX idx_embeddings_vector ON record_embeddings 
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 用户向量索引
CREATE INDEX idx_embeddings_user_id ON record_embeddings(user_id);
```

## 安全机制

### 1. 认证安全
- JWT令牌有效期：访问令牌15分钟，刷新令牌7天
- 密码强度验证（最少8位，包含大小写字母和数字）
- bcrypt加密（cost factor: 12）
- 防暴力破解（登录失败限制）

### 2. 数据安全
- Row Level Security (RLS) 策略
- 用户只能访问自己的数据
- SQL注入防护
- XSS防护

### 3. API安全
- HTTPS强制
- CORS配置
- 请求频率限制
- 输入验证和清理

## 向量化处理流程

```
用户上传文本记录
    ↓
保存到daily_records表
    ↓
触发异步任务
    ↓
调用OpenAI API生成embedding
    ↓
存储到record_embeddings表
    ↓
返回成功响应
```

## RAG检索流程

```
用户请求总结
    ↓
生成查询embedding
    ↓
pgvector相似度搜索
    ↓
检索Top-K相关记录
    ↓
构建上下文prompt
    ↓
调用LLM生成总结
    ↓
返回个性化总结
```

## 部署架构

### 开发环境
```
本地开发 → Supabase本地实例 → 测试
```

### 生产环境
```
云服务器 → Supabase云服务 → 负载均衡 → CDN
```

## 性能优化

### 1. 数据库优化
- 连接池管理
- 查询优化
- 索引优化
- 分页查询

### 2. 缓存策略
- Redis缓存用户会话
- 缓存常用查询结果
- 向量搜索结果缓存

### 3. 异步处理
- 向量化异步队列
- 批量处理优化
- 后台任务调度

## 监控和日志

### 监控指标
- API响应时间
- 数据库查询性能
- 向量化处理速度
- 错误率统计

### 日志记录
- 用户操作日志
- 系统错误日志
- 安全审计日志
- 性能分析日志

## 扩展性考虑

### 水平扩展
- 无状态API设计
- 数据库读写分离
- 微服务架构准备

### 功能扩展
- 多模态支持（图片、音频）
- 协作功能
- 数据导出
- 高级分析
