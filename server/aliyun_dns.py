#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import requests
from aliyunsdkcore.client import AcsClient
from aliyunsdkcore.acs_exception.exceptions import ClientException
from aliyunsdkcore.acs_exception.exceptions import ServerException
from aliyunsdkalidns.request.v20150109.DescribeDomainRecordsRequest import DescribeDomainRecordsRequest
from aliyunsdkalidns.request.v20150109.UpdateDomainRecordRequest import UpdateDomainRecordRequest
from aliyunsdkalidns.request.v20150109.AddDomainRecordRequest import AddDomainRecordRequest
import json
import os
import logging
import subprocess
import re
import time

# ############### 配置区域 ###############

# 请修改以下变量为您自己的配置
ACCESS_KEY_ID = ''
ACCESS_KEY_SECRET = ''
DOMAIN_NAME = ''  # 你的主域名
RR = 'www'  # 主机记录，例如想解析ddns.example.com，这里就填'ddns'
# #######################################

# 获取公网IP的服務列表
IP_SERVICES = [
    'https://icanhazip.com',
    'https://ident.me',
    'https://myip.ipip.net',
    'https://api.ipify.org',
]

# 设置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(rf'{os.path.dirname(__file__)}\aliyun_ddns.log'),  # 日志文件路径，可根据需要修改
        logging.StreamHandler()  # 同时在控制台输出
    ]
)
logger = logging.getLogger(__name__)

def get_ipv6_locally():
    """通过系统命令获取本地IPv6地址（不一定是公网出口IP）"""
    try:
        # 执行 ipconfig 命令并获取输出
        result = subprocess.run(['ipconfig', '/all'], capture_output=True, text=True, check=True, timeout=10)
        output = result.stdout
        
        # 使用正则表达式查找可能的公网IPv6地址（通常不是以fe80开头的链路本地地址）
        # 这个正则匹配非fe80开头的IPv6地址，但可能包含非公网地址
        ipv6_pattern = r'(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4})'
        matches = re.findall(ipv6_pattern, output)
        for match in matches:
            candidate = match[0]
            if not candidate.lower().startswith('fe80'):  # 过滤掉链路本地地址
                print(f"[找到] 本地IPv6地址: {candidate}")
                return candidate
        
        print("未在本地找到符合条件的IPv6地址")
        return None
        
    except subprocess.CalledProcessError:
        print("执行 ipconfig 命令失败")
        return None
    except subprocess.TimeoutExpired:
        print("命令执行超时")
        return None

def get_public_ipv6_robust(retries=3):
    """
    健壮地获取公网IPv6地址，包含多重备选方案
    :param retries: 重试次数
    :return: IPv6地址字符串，失败返回None
    """
    # 方案1: 在线API服务 (优先级从高到低)
    online_services = [
        {'url': 'https://v6.ident.me', 'type': 'text'},
        {'url': 'https://api6.ipify.org?format=json', 'type': 'json'},
        {'url': 'https://ipv6.icanhazip.com', 'type': 'text'},
        {'url': 'http://getip6.china-ipv6.cn:5010', 'type': 'text'},  # 国内服务
        {'url': 'http://jsonip.com', 'type': 'json'}  # 注意此为HTTP
    ]
    
    for attempt in range(retries):
        print(f"\n=== 尝试获取公网IPv6 (第 {attempt + 1} 次) ===")
        
        # 优先尝试在线API
        for service in online_services:
            try:
                print(f"尝试服务: {service['url']}")
                response = requests.get(service['url'], timeout=8)
                if response.status_code == 200:
                    if service['type'] == 'json':
                        ip = response.json().get('ip', '').strip()
                    else:
                        ip = response.text.strip()
                    
                    # 验证获取的是否为IPv6地址
                    if ip and ':' in ip and not ip.startswith('fe80'):
                        print(f"✅ 成功从 {service['url']} 获取到公网IPv6: {ip}")
                        return ip
                    else:
                        print(f"服务返回内容未识别为IPv6: {ip}")
            except Exception as e:
                print(f"服务 {service['url']} 请求失败: {e}")
                continue
        
        # 如果所有在线服务都失败了，尝试本地查找（最后一次重试时）
        if attempt == retries - 1:
            print("在线服务均失败，尝试本地查找...")
            local_ipv6 = get_ipv6_locally()
            if local_ipv6:
                print(f"⚠️ 注意：此为本地IPv6地址，可能非公网出口IP: {local_ipv6}")
                return local_ipv6
            else:
                print("❌ 所有方案均失败，无法获取IPv6地址")
                return None
        
        print(f"第 {attempt + 1} 轮尝试失败，{3 - attempt}秒后重试...")
        time.sleep(3)  # 等待几秒后重试

def get_current_ip():
    """
    从多个服务获取当前网络的公网IPv4地址
    """
    for service in IP_SERVICES:
        try:
            response = requests.get(service, timeout=10)
            response.raise_for_status()
            # 清理获取到的IP（去除空格和换行符）
            current_ip = response.text.strip()
            # 简单验证一下是否是IPv4地址
            if current_ip.count('.') == 3 and all(0 <= int(part) < 256 for part in current_ip.split('.')):
                logger.info(f"成功获取到公网IP: {current_ip} (来自: {service})")
                return current_ip
            else:
                logger.warning(f"从 {service} 获取到的内容不是有效的IPv4地址: {response.text}")
        except requests.RequestException as e:
            logger.warning(f"从 {service} 获取IP失败: {e}")
            continue

    logger.error("所有IP查询服务均失败，无法获取公网IP地址！")
    return None

def get_dns_record_info(client, domain_name, rr):
    """
    获取指定解析记录的信息，返回RecordId和当前的IP
    """
    request = DescribeDomainRecordsRequest()
    request.set_accept_format('json')
    request.set_DomainName(domain_name)
    request.set_RRKeyWord(rr)
    request.set_TypeKeyWord('AAAA')

    try:
        response = client.do_action_with_exception(request)
        result = json.loads(response)
        records = result.get('DomainRecords', {}).get('Record', [])
        
        if records:
            record = records[0]
            return record.get('RecordId'), record.get('Value')
        else:
            logger.info(f"未找到主机记录为 '{rr}.{domain_name}' 的AAAA记录，可能需要新建。")
            return None, None

    except (ClientException, ServerException) as e:
        logger.error(f"查询DNS记录时发生API错误: {e}")
        return None, None

def update_dns_record(client, record_id, rr, record_type, value):
    """
    更新已有的解析记录
    """
    request = UpdateDomainRecordRequest()
    request.set_accept_format('json')
    request.set_RecordId(record_id)
    request.set_RR(rr)
    request.set_Type(record_type)
    request.set_Value(value)

    try:
        response = client.do_action_with_exception(request)
        logger.info(f"DNS记录更新成功！已将 {rr}.{DOMAIN_NAME} 指向 {value}")
        return True
    except (ClientException, ServerException) as e:
        logger.error(f"更新DNS记录时发生API错误: {e}")
        return False

def add_dns_record(client, rr, domain_name, record_type, value):
    """
    添加新的解析记录
    """
    request = AddDomainRecordRequest()
    request.set_accept_format('json')
    request.set_DomainName(domain_name)
    request.set_RR(rr)
    request.set_Type(record_type)
    request.set_Value(value)

    try:
        response = client.do_action_with_exception(request)
        logger.info(f"DNS记录新建成功！已将 {rr}.{domain_name} 指向 {value}")
        return True
    except (ClientException, ServerException) as e:
        logger.error(f"新建DNS记录时发生API错误: {e}")
        return False

def main():
    # 1. 获取当前公网IP
    current_ip = get_public_ipv6_robust()
    if not current_ip:
        return

    # 2. 初始化阿里云客户端
    client = AcsClient(ACCESS_KEY_ID, ACCESS_KEY_SECRET, 'cn-hangzhou')

    # 3. 查询现有的解析记录
    record_id, existing_ip = get_dns_record_info(client, DOMAIN_NAME, RR)

    # 4. 判断IP是否发生变化
    if existing_ip == current_ip:
        logger.info(f"公网IP未发生变化，无需更新。当前IP: {current_ip}")
        return

    # 5. IP已变化，执行更新或新建操作
    if record_id:
        # 更新已有记录
        success = update_dns_record(client, record_id, RR, 'AAAA', current_ip)
    else:
        # 新建记录
        success = add_dns_record(client, RR, DOMAIN_NAME, 'AAA', current_ip)

    if success:
        logger.info("DDNS更新流程完成！")
    else:
        logger.error("DDNS更新失败！")

if __name__ == '__main__':
    while True:
        main()
        # 每隔10分钟检查一次IP变化
        print("等待10分钟后再次检查IP变化...")
        time.sleep(600)

