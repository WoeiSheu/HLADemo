#### To do:
3. Object Instance Name 命名方式, 以及Object Instance使用问题

4. nextMessageRequest() 使用问题

5. 设计有限关联的时间管理策略

9. 连接数据库

#### Have done:
1. 分离FederateParameters和FederateAttributes, 构造时设置FederateAttributes值, FederateParameters只在构造时传入, 之后不再使用
2. 返回FOM Name, 前端显示, 点击可查看Fom, target 改为 blank
3. 获取主机名
4. RTI Tab 页面显示连接的联邦成员
5. Update step或lookahead后导致其他联邦成员改变, 还是第二次Update导致的?
  Update之后时间可能一步推进到最大? 但是Federate里的timeToMoveTo还没有改变