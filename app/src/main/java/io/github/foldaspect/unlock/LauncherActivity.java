package io.github.foldaspect.unlock;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class LauncherActivity extends Activity {
    private LinearLayout content;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(245, 247, 250));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(245, 247, 250));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);
        text("画幅切换解锁", 28, Color.rgb(24, 35, 50));
        text("小米 18 Fold · 0.1.0 测试版", 15, Color.DKGRAY);
        boolean supported = UnlockPolicy.supports(Build.DEVICE, Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL);
        text(supported ? "系统版本匹配" : "当前系统版本不在适配范围，模块不会加载", 19,
                supported ? Color.rgb(20, 110, 75) : Color.rgb(165, 60, 40));
        text("当前版本：" + Build.VERSION.INCREMENTAL + "\n适配版本：" + UnlockPolicy.TARGET_BUILD, 14, Color.DKGRAY);
        text("启用方法", 21, Color.BLACK);
        text("1. 在 LSPosed 中开启“画幅切换解锁”。\n2. 作用域只选择“系统框架（android）”。\n3. 重启手机。\n4. 打开应用，在控制中心点击原有的“画幅切换”。", 17, Color.DKGRAY);
        text("此页面只能确认版本匹配，不能确认系统框架中的模块已激活。", 14, Color.DKGRAY);
        Button manager = new Button(this);
        manager.setText("打开 LSPosed");
        manager.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
            if (intent == null) Toast.makeText(this, "请从桌面或 LSPosed 通知打开管理器", Toast.LENGTH_LONG).show();
            else startActivity(intent);
        });
        content.addView(manager);
        text("解除哪些限制", 21, Color.BLACK);
        text("解除应用禁用名单、普通预装应用判定，以及拍照、扫码等页面的强制全屏名单。系统保留原有的画幅切换按钮和比例计算。", 17, Color.DKGRAY);
        text("使用范围", 21, Color.BLACK);
        text("适用于系统允许的内外屏竖屏、全屏应用场景。桌面、锁屏、分屏、固定横屏和已有独立游戏比例配置仍按系统规则处理。解除限制不代表所有应用都能正确适配，尤其需要检查相机预览和扫码页面。", 16, Color.DKGRAY);
        text("恢复原状", 21, Color.BLACK);
        text("先将测试过的应用切回全屏，再在 LSPosed 停用模块并重启。模块不替换系统文件；系统记住的每个应用画幅选择不会自动清除。", 16, Color.DKGRAY);
    }

    private void text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, 16, 0, 14);
        view.setLineSpacing(4, 1.12f);
        content.addView(view);
    }
}
