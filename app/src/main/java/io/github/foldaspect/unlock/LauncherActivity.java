package io.github.foldaspect.unlock;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class LauncherActivity extends Activity {
    private LinearLayout content;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(245, 247, 250));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        // API 35+ draws edge-to-edge. Keep this instruction page below the real
        // system insets, including the scaled insets supplied in aspect mode.
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.BLACK);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) scroll.getLayoutParams();
            if (layout.leftMargin != safe.left || layout.topMargin != safe.top
                    || layout.rightMargin != safe.right || layout.bottomMargin != safe.bottom) {
                layout.setMargins(safe.left, safe.top, safe.right, safe.bottom);
                scroll.setLayoutParams(layout);
            }
            return insets;
        });
        setContentView(page);
        WindowInsetsController systemBars = getWindow().getInsetsController();
        if (systemBars != null) {
            systemBars.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        page.requestApplyInsets();
        text("画幅切换解锁", 28, Color.rgb(24, 35, 50));
        text("小米 18 Fold · 0.2.0 测试版", 15, Color.DKGRAY);
        boolean supported = UnlockPolicy.supports(Build.DEVICE, Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL);
        text(supported ? "系统版本匹配" : "当前系统版本不在适配范围，模块不会加载", 19,
                supported ? Color.rgb(20, 110, 75) : Color.rgb(165, 60, 40));
        text("当前版本：" + Build.VERSION.INCREMENTAL + "\n适配版本：" + UnlockPolicy.TARGET_BUILD, 14, Color.DKGRAY);
        text("启用方法", 21, Color.BLACK);
        text("1. 在 LSPosed 中开启“画幅切换解锁”。\n2. 作用域勾选“系统框架”和“系统界面”。\n3. 完整重启手机。\n4. 打开应用，在控制中心点击原有的“画幅切换”。", 17, Color.DKGRAY);
        text("此页面只能确认版本匹配，不能确认系统框架中的模块已激活。", 14, Color.DKGRAY);
        Button manager = new Button(this);
        manager.setText("打开 LSPosed");
        manager.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
            if (intent == null) Toast.makeText(this, "请从桌面或 LSPosed 通知打开管理器", Toast.LENGTH_LONG).show();
            else startActivity(intent);
        });
        content.addView(manager);
        text("内外屏自动切换", 21, Color.BLACK);
        text("展开内屏时自动恢复默认全屏；折回外屏后恢复该应用原来的画幅选择。外屏设置会保留，无需重新开启。内屏展开期间不启用画幅模式。", 17, Color.DKGRAY);
        text("保留状态栏", 21, Color.BLACK);
        text("画幅模式不再额外隐藏顶部状态栏。视频、游戏等应用自己请求的沉浸式全屏仍按系统原有行为处理。", 17, Color.DKGRAY);
        text("外屏画幅模式使用 20% 不透明深色底和白色图标，状态栏目标高度为 90 像素，图标尺寸保持不变。较大字号会保留足够高度。退出画幅后恢复原有样式。", 17, Color.DKGRAY);
        text("紧凑高度和更浅底色需要勾选“系统界面”作用域。摄像头安全区保留，部分应用仍会按安全区预留顶部空间。", 14, Color.DKGRAY);
        text("解除哪些限制", 21, Color.BLACK);
        text("解除应用禁用名单、普通预装应用判定，以及拍照、扫码等页面的强制全屏名单。系统保留原有的画幅切换按钮和比例计算。", 17, Color.DKGRAY);
        text("使用范围", 21, Color.BLACK);
        text("画幅切换适用于系统允许的外屏竖屏、全屏应用场景。桌面、锁屏、分屏、固定横屏和已有独立游戏比例配置仍按系统规则处理。解除限制不代表所有应用都能正确适配，尤其需要检查相机预览和扫码页面。", 16, Color.DKGRAY);
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
