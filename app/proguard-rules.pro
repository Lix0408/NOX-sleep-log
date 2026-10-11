# NOX 的 R8 规则。
#
# 现状：除下面一条外，全部依赖各库自带的 consumer 规则 ——
#   * Room / Compose / DataStore / Coroutines 均自带 keep 规则（KSP 生成的实现类同样被覆盖）
#   * Service、Activity、AppWidgetProvider 通过 AndroidManifest 注册，AGP 自动 keep
#   * 项目内无反射、无序列化魔法（JSON 导出为手写字符串拼接）
# 若未来引入需要反射的库（如 Gson 数据类），在此补充对应的 -keep 规则。
#
# 注意：每次发布都要归档 app/build/outputs/mapping/release/mapping.txt，
# 否则线上崩溃的堆栈无法还原。
-dontwarn org.jetbrains.annotations.**
