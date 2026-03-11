"""
Android应用后端API服务
基于FastAPI + Supabase实现用户认证、日常记录管理和RAG知识库功能
"""

from fastapi import FastAPI, HTTPException, Depends, status, BackgroundTasks
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, EmailStr, Field, validator
from typing import Optional, List, Dict, Any
from datetime import datetime, timedelta
import os
import re
from supabase import create_client, Client
import openai
from dotenv import load_dotenv
import logging

# 加载环境变量
load_dotenv()

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 初始化FastAPI应用
app = FastAPI(
    title="Daily Recorder API",
    description="基于Supabase的Android应用后端API",
    version="1.0.0"
)

# CORS配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应限制具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 初始化Supabase客户端
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")
supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)

# 初始化OpenAI客户端
openai.api_key = os.getenv("OPENAI_API_KEY")

# 安全认证
security = HTTPBearer()

# ==================== 数据模型 ====================

class UserRegister(BaseModel):
    email: Optional[EmailStr] = None
    phone: Optional[str] = None
    password: str = Field(..., min_length=8)
    
    @validator('password')
    def validate_password(cls, v):
        if not re.search(r'[A-Z]', v):
            raise ValueError('密码必须包含至少一个大写字母')
        if not re.search(r'[a-z]', v):
            raise ValueError('密码必须包含至少一个小写字母')
        if not re.search(r'\d', v):
            raise ValueError('密码必须包含至少一个数字')
        return v
    
    @validator('phone')
    def validate_phone(cls, v):
        if v and not re.match(r'^1[3-9]\d{9}$', v):
            raise ValueError('手机号格式不正确')
        return v

class UserLogin(BaseModel):
    email: Optional[EmailStr] = None
    phone: Optional[str] = None
    password: str

class RecordCreate(BaseModel):
    content: str = Field(..., min_length=1, max_length=10000)
    metadata: Optional[Dict[str, Any]] = {}

class RecordUpdate(BaseModel):
    content: str = Field(..., min_length=1, max_length=10000)
    metadata: Optional[Dict[str, Any]] = {}

class RecordResponse(BaseModel):
    id: str
    user_id: str
    content: str
    created_at: datetime
    updated_at: datetime
    metadata: Dict[str, Any]

class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1)
    limit: int = Field(default=10, ge=1, le=50)
    threshold: float = Field(default=0.7, ge=0.0, le=1.0)

class SummaryRequest(BaseModel):
    date: Optional[str] = None  # YYYY-MM-DD格式
    days: int = Field(default=1, ge=1, le=30)

# ==================== 认证工具函数 ====================

async def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)):
    """验证JWT令牌并获取当前用户"""
    try:
        token = credentials.credentials
        user = supabase.auth.get_user(token)
        if not user:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="无效的认证令牌"
            )
        return user
    except Exception as e:
        logger.error(f"认证失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="认证失败"
        )

# ==================== 向量化处理 ====================

async def generate_embedding(text: str) -> List[float]:
    """使用OpenAI API生成文本向量"""
    try:
        response = openai.Embedding.create(
            model="text-embedding-ada-002",
            input=text
        )
        return response['data'][0]['embedding']
    except Exception as e:
        logger.error(f"向量生成失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="向量生成失败"
        )

async def process_record_embedding(record_id: str, user_id: str, content: str):
    """异步处理记录向量化"""
    try:
        # 生成向量
        embedding = await generate_embedding(content)
        
        # 存储向量
        supabase.table('record_embeddings').insert({
            'record_id': record_id,
            'user_id': user_id,
            'embedding': embedding
        }).execute()
        
        logger.info(f"记录 {record_id} 向量化完成")
    except Exception as e:
        logger.error(f"向量化处理失败: {str(e)}")

# ==================== 认证API ====================

@app.post("/api/auth/register", status_code=status.HTTP_201_CREATED)
async def register(user_data: UserRegister):
    """用户注册"""
    try:
        # 使用邮箱或手机号注册
        if user_data.email:
            response = supabase.auth.sign_up({
                "email": user_data.email,
                "password": user_data.password
            })
        elif user_data.phone:
            response = supabase.auth.sign_up({
                "phone": user_data.phone,
                "password": user_data.password
            })
        else:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="必须提供邮箱或手机号"
            )
        
        return {
            "message": "注册成功",
            "user": response.user,
            "session": response.session
        }
    except Exception as e:
        logger.error(f"注册失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"注册失败: {str(e)}"
        )

@app.post("/api/auth/login")
async def login(credentials: UserLogin):
    """用户登录"""
    try:
        if credentials.email:
            response = supabase.auth.sign_in_with_password({
                "email": credentials.email,
                "password": credentials.password
            })
        elif credentials.phone:
            response = supabase.auth.sign_in_with_password({
                "phone": credentials.phone,
                "password": credentials.password
            })
        else:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="必须提供邮箱或手机号"
            )
        
        return {
            "message": "登录成功",
            "access_token": response.session.access_token,
            "refresh_token": response.session.refresh_token,
            "user": response.user
        }
    except Exception as e:
        logger.error(f"登录失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="登录失败，请检查凭据"
        )

@app.post("/api/auth/refresh")
async def refresh_token(refresh_token: str):
    """刷新访问令牌"""
    try:
        response = supabase.auth.refresh_session(refresh_token)
        return {
            "access_token": response.session.access_token,
            "refresh_token": response.session.refresh_token
        }
    except Exception as e:
        logger.error(f"令牌刷新失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="令牌刷新失败"
        )

@app.post("/api/auth/logout")
async def logout(user=Depends(get_current_user)):
    """用户登出"""
    try:
        supabase.auth.sign_out()
        return {"message": "登出成功"}
    except Exception as e:
        logger.error(f"登出失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="登出失败"
        )

@app.get("/api/auth/profile")
async def get_profile(user=Depends(get_current_user)):
    """获取用户信息"""
    return {"user": user}

# ==================== 日常记录API ====================

@app.post("/api/records", response_model=RecordResponse, status_code=status.HTTP_201_CREATED)
async def create_record(
    record: RecordCreate,
    background_tasks: BackgroundTasks,
    user=Depends(get_current_user)
):
    """创建日常记录"""
    try:
        # 插入记录
        response = supabase.table('daily_records').insert({
            'user_id': user.user.id,
            'content': record.content,
            'metadata': record.metadata
        }).execute()
        
        record_data = response.data[0]
        
        # 异步处理向量化
        background_tasks.add_task(
            process_record_embedding,
            record_data['id'],
            user.user.id,
            record.content
        )
        
        return record_data
    except Exception as e:
        logger.error(f"创建记录失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="创建记录失败"
        )

@app.get("/api/records", response_model=List[RecordResponse])
async def get_records(
    skip: int = 0,
    limit: int = 20,
    user=Depends(get_current_user)
):
    """获取用户所有记录"""
    try:
        response = supabase.table('daily_records')\
            .select('*')\
            .eq('user_id', user.user.id)\
            .order('created_at', desc=True)\
            .range(skip, skip + limit - 1)\
            .execute()
        
        return response.data
    except Exception as e:
        logger.error(f"获取记录失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="获取记录失败"
        )

@app.get("/api/records/{record_id}", response_model=RecordResponse)
async def get_record(record_id: str, user=Depends(get_current_user)):
    """获取单条记录"""
    try:
        response = supabase.table('daily_records')\
            .select('*')\
            .eq('id', record_id)\
            .eq('user_id', user.user.id)\
            .single()\
            .execute()
        
        if not response.data:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="记录不存在"
            )
        
        return response.data
    except Exception as e:
        logger.error(f"获取记录失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="获取记录失败"
        )

@app.put("/api/records/{record_id}", response_model=RecordResponse)
async def update_record(
    record_id: str,
    record: RecordUpdate,
    background_tasks: BackgroundTasks,
    user=Depends(get_current_user)
):
    """更新记录"""
    try:
        response = supabase.table('daily_records')\
            .update({
                'content': record.content,
                'metadata': record.metadata
            })\
            .eq('id', record_id)\
            .eq('user_id', user.user.id)\
            .execute()
        
        if not response.data:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="记录不存在"
            )
        
        # 重新生成向量
        background_tasks.add_task(
            process_record_embedding,
            record_id,
            user.user.id,
            record.content
        )
        
        return response.data[0]
    except Exception as e:
        logger.error(f"更新记录失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="更新记录失败"
        )

@app.delete("/api/records/{record_id}")
async def delete_record(record_id: str, user=Depends(get_current_user)):
    """删除记录"""
    try:
        response = supabase.table('daily_records')\
            .delete()\
            .eq('id', record_id)\
            .eq('user_id', user.user.id)\
            .execute()
        
        return {"message": "记录已删除"}
    except Exception as e:
        logger.error(f"删除记录失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="删除记录失败"
        )

# ==================== RAG知识库API ====================

@app.post("/api/rag/search")
async def semantic_search(
    search_req: SearchRequest,
    user=Depends(get_current_user)
):
    """语义搜索"""
    try:
        # 生成查询向量
        query_embedding = await generate_embedding(search_req.query)
        
        # 调用向量搜索函数
        response = supabase.rpc('match_records', {
            'query_embedding': query_embedding,
            'match_threshold': search_req.threshold,
            'match_count': search_req.limit,
            'filter_user_id': user.user.id
        }).execute()
        
        return {
            "query": search_req.query,
            "results": response.data
        }
    except Exception as e:
        logger.error(f"语义搜索失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="语义搜索失败"
        )

@app.post("/api/rag/summary")
async def generate_summary(
    summary_req: SummaryRequest,
    user=Depends(get_current_user)
):
    """生成每日总结"""
    try:
        # 获取指定日期范围的记录
        target_date = summary_req.date or datetime.now().strftime('%Y-%m-%d')
        
        response = supabase.table('daily_records')\
            .select('content, created_at')\
            .eq('user_id', user.user.id)\
            .gte('created_at', target_date)\
            .lte('created_at', f"{target_date} 23:59:59")\
            .order('created_at')\
            .execute()
        
        if not response.data:
            return {"message": "该日期没有记录", "summary": None}
        
        # 构建上下文
        records_text = "\n\n".join([
            f"[{r['created_at']}] {r['content']}"
            for r in response.data
        ])
        
        # 调用LLM生成总结
        prompt = f"""基于以下用户的日常记录，生成一份个性化的每日总结。
总结应包括：
1. 主要活动和事件
2. 情绪和感受
3. 重要洞察
4. 建议和反思

用户记录：
{records_text}

请用中文生成简洁、有洞察力的总结："""
        
        completion = openai.ChatCompletion.create(
            model="gpt-3.5-turbo",
            messages=[
                {"role": "system", "content": "你是一个善于总结和分析的AI助手。"},
                {"role": "user", "content": prompt}
            ],
            temperature=0.7,
            max_tokens=500
        )
        
        summary = completion.choices[0].message.content
        
        return {
            "date": target_date,
            "record_count": len(response.data),
            "summary": summary
        }
    except Exception as e:
        logger.error(f"生成总结失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="生成总结失败"
        )

@app.get("/api/rag/insights")
async def get_insights(user=Depends(get_current_user)):
    """获取个性化洞察"""
    try:
        # 获取用户统计信息
        stats = supabase.table('user_record_stats')\
            .select('*')\
            .eq('user_id', user.user.id)\
            .single()\
            .execute()
        
        return {
            "user_id": user.user.id,
            "statistics": stats.data if stats.data else {}
        }
    except Exception as e:
        logger.error(f"获取洞察失败: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="获取洞察失败"
        )

# ==================== 健康检查 ====================

@app.get("/health")
async def health_check():
    """健康检查端点"""
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat()
    }

@app.get("/")
async def root():
    """根路径"""
    return {
        "message": "Daily Recorder API",
        "version": "1.0.0",
        "docs": "/docs"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
