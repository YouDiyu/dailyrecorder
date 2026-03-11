-- Supabase数据库初始化脚本
-- 执行此脚本前确保已启用pgvector扩展

-- 1. 启用必要的扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- 2. 创建日常记录表
CREATE TABLE IF NOT EXISTS public.daily_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'::jsonb,
    CONSTRAINT content_not_empty CHECK (length(trim(content)) > 0)
);

-- 3. 创建记录向量表
CREATE TABLE IF NOT EXISTS public.record_embeddings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    record_id UUID NOT NULL REFERENCES public.daily_records(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    embedding vector(1536) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT unique_record_embedding UNIQUE (record_id)
);

-- 4. 创建索引
-- 日常记录索引
CREATE INDEX IF NOT EXISTS idx_records_user_id ON public.daily_records(user_id);
CREATE INDEX IF NOT EXISTS idx_records_created_at ON public.daily_records(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_records_user_created ON public.daily_records(user_id, created_at DESC);

-- 向量索引（使用IVFFlat算法进行近似最近邻搜索）
CREATE INDEX IF NOT EXISTS idx_embeddings_vector ON public.record_embeddings 
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 用户向量关联索引
CREATE INDEX IF NOT EXISTS idx_embeddings_user_id ON public.record_embeddings(user_id);
CREATE INDEX IF NOT EXISTS idx_embeddings_record_id ON public.record_embeddings(record_id);

-- 5. 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 6. 为daily_records表添加更新时间触发器
DROP TRIGGER IF EXISTS update_daily_records_updated_at ON public.daily_records;
CREATE TRIGGER update_daily_records_updated_at
    BEFORE UPDATE ON public.daily_records
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 7. 启用Row Level Security (RLS)
ALTER TABLE public.daily_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.record_embeddings ENABLE ROW LEVEL SECURITY;

-- 8. 创建RLS策略 - daily_records表
-- 用户只能查看自己的记录
CREATE POLICY "Users can view own records" ON public.daily_records
    FOR SELECT
    USING (auth.uid() = user_id);

-- 用户只能插入自己的记录
CREATE POLICY "Users can insert own records" ON public.daily_records
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- 用户只能更新自己的记录
CREATE POLICY "Users can update own records" ON public.daily_records
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- 用户只能删除自己的记录
CREATE POLICY "Users can delete own records" ON public.daily_records
    FOR DELETE
    USING (auth.uid() = user_id);

-- 9. 创建RLS策略 - record_embeddings表
-- 用户只能查看自己的向量
CREATE POLICY "Users can view own embeddings" ON public.record_embeddings
    FOR SELECT
    USING (auth.uid() = user_id);

-- 用户只能插入自己的向量
CREATE POLICY "Users can insert own embeddings" ON public.record_embeddings
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- 用户只能删除自己的向量
CREATE POLICY "Users can delete own embeddings" ON public.record_embeddings
    FOR DELETE
    USING (auth.uid() = user_id);

-- 10. 创建向量相似度搜索函数
CREATE OR REPLACE FUNCTION match_records(
    query_embedding vector(1536),
    match_threshold float,
    match_count int,
    filter_user_id uuid
)
RETURNS TABLE (
    id uuid,
    record_id uuid,
    content text,
    similarity float,
    created_at timestamp with time zone
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        re.id,
        re.record_id,
        dr.content,
        1 - (re.embedding <=> query_embedding) as similarity,
        dr.created_at
    FROM record_embeddings re
    JOIN daily_records dr ON re.record_id = dr.id
    WHERE re.user_id = filter_user_id
        AND 1 - (re.embedding <=> query_embedding) > match_threshold
    ORDER BY re.embedding <=> query_embedding
    LIMIT match_count;
END;
$$;

-- 11. 创建用户统计视图
CREATE OR REPLACE VIEW user_record_stats AS
SELECT
    user_id,
    COUNT(*) as total_records,
    COUNT(DISTINCT DATE(created_at)) as active_days,
    MIN(created_at) as first_record_date,
    MAX(created_at) as last_record_date
FROM public.daily_records
GROUP BY user_id;

-- 12. 授予必要的权限
GRANT USAGE ON SCHEMA public TO authenticated;
GRANT ALL ON public.daily_records TO authenticated;
GRANT ALL ON public.record_embeddings TO authenticated;
GRANT SELECT ON user_record_stats TO authenticated;

-- 13. 创建记录统计函数
CREATE OR REPLACE FUNCTION get_user_daily_summary(
    target_user_id uuid,
    target_date date
)
RETURNS TABLE (
    record_count bigint,
    total_characters bigint,
    first_record_time timestamp with time zone,
    last_record_time timestamp with time zone
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*) as record_count,
        SUM(length(content)) as total_characters,
        MIN(created_at) as first_record_time,
        MAX(created_at) as last_record_time
    FROM public.daily_records
    WHERE user_id = target_user_id
        AND DATE(created_at) = target_date;
END;
$$;

-- 完成提示
DO $$
BEGIN
    RAISE NOTICE '数据库初始化完成！';
    RAISE NOTICE '已创建表: daily_records, record_embeddings';
    RAISE NOTICE '已启用RLS安全策略';
    RAISE NOTICE '已创建向量搜索函数: match_records';
END $$;
