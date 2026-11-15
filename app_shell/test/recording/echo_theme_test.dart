import 'package:echofold_app/ui/theme/echo_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('主题中的可用与禁用按钮均保留至少 48dp 触控目标',
      (WidgetTester tester) async {
    await tester.pumpWidget(MaterialApp(
      theme: EchoTheme.dark,
      home: Scaffold(
        body: Column(mainAxisSize: MainAxisSize.min, children: <Widget>[
          TextButton(onPressed: () {}, child: const Text('取消')),
          OutlinedButton(onPressed: null, child: const Text('不可用')),
          IconButton(onPressed: () {}, icon: const Icon(Icons.close)),
        ]),
      ),
    ));

    for (final Finder button in <Finder>[
      find.byType(TextButton),
      find.byType(OutlinedButton),
      find.byType(IconButton),
    ]) {
      final Size size = tester.getSize(button);
      expect(size.width, greaterThanOrEqualTo(48));
      expect(size.height, greaterThanOrEqualTo(48));
    }
  });
}
