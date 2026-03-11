telnet 192.168.1.1
telnetadmin
telnetadmin
# 清空IPv6防火墙规则
ip6tables -F
# 设置默认策略为接受
ip6tables -P INPUT ACCEPT
ip6tables -P FORWARD ACCEPT
ip6tables -P OUTPUT ACCEPT