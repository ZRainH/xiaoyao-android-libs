# =============================================================================
# Android UI 组件：Activity / Fragment / View / 布局反射
# 说明：
# - Manifest 里登记的四大组件，AGP 默认规则一般会 keep 类名；
#   这里再补「XML 布局反射创建」与「无参/多参构造」等高频坑。
# - 只保留必要构造与入口方法，避免整类 public * 导致无法混淆业务方法。
# =============================================================================

# ---------- Activity ----------
# 保留四大组件类名（含未写进 Manifest、运行时动态启动的兜底）
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# AppCompat / ComponentActivity 体系
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends androidx.activity.ComponentActivity
-keep public class * extends androidx.fragment.app.FragmentActivity

# XML android:onClick="xxx" 按方法名反射调用，方法名不能混淆
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
-keepclassmembers class * extends androidx.appcompat.app.AppCompatActivity {
    public void *(android.view.View);
}

# ---------- Fragment ----------
# Fragment 必须保留无参构造（系统 / FragmentManager 反射 newInstance）
-keep public class * extends android.app.Fragment {
    public <init>();
}
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
}
-keep public class * extends androidx.fragment.app.DialogFragment {
    public <init>();
}
-keep public class * extends androidx.preference.PreferenceFragmentCompat {
    public <init>();
}

# 布局里 <fragment android:name="..." /> / class= 按类名查找
-keepnames class * extends androidx.fragment.app.Fragment
-keepnames class * extends android.app.Fragment

# ---------- 自定义 View ----------
# LayoutInflater 按 XML 全类名反射，并调用 Context / AttributeSet 构造
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
}
-keep public class * extends android.view.ViewGroup {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
}

# 若自定义 View 的属性 setter 仅被 dataBinding / 反射调用，请在宿主 rules 里对具体类补 keep


# ---------- Adapter / ViewHolder ----------
# RecyclerView.Adapter / ViewHolder 常被反射或被库按类使用
-keep public class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    public <init>(...);
}
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$Adapter {
    <init>(...);
}

# ---------- Parcelable / Serializable / Enum ----------
-keepclassmembers class * implements android.os.Parcelable {
    public static ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- WebView JS 桥 ----------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------- native ----------
-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn androidx.appcompat.**
-dontwarn androidx.fragment.**
-dontwarn androidx.recyclerview.**
-dontwarn androidx.preference.**
