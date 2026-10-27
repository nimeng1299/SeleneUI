# SeleneUI

`Button`、`Card`、`InfoCard` 和 Gallery 导航项的可见文字使用 `Text`，可直接配置颜色、字号、对齐和阴影。

```java
new Button(0, 0, 120, 32, new Text("确定").TextScale(1.2f));
new Card(Text.FromComponent(Component.translatable("example.title")));
new InfoCard(new Text("Lv.90").Shadow(true));
```

`Text.FromComponent(Component)` 保留原生翻译、富文本样式和子节点。可通过 `getText()` 修改文字，或用 `setText(Text)` 替换；原生 `setMessage(Component)` 同样会更新当前 `Text`。
