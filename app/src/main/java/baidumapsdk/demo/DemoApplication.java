package baidumapsdk.demo;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;

import java.io.File;

import static android.content.ContentValues.TAG;


public class DemoApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        File f  = getDir("lib", Context.MODE_PRIVATE);
        patch(this,f);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 在使用 SDK 各组间之前初始化 context 信息，传入 ApplicationContext
        // 默认本地个性化地图初始化方法
        SDKInitializer.initialize(this);

        //自4.3.0起，百度地图SDK所有接口均支持百度坐标和国测局坐标，用此方法设置您使用的坐标类型.
        //包括BD09LL和GCJ02两种坐标，默认是BD09LL坐标。
        SDKInitializer.setCoordType(CoordType.BD09LL);
    }

    public boolean patch(Application application, File dir) {

        try {
            // 获取Application的BaseContext （来自ContextWrapper）
            Context oBase = application.getBaseContext();
            if (oBase == null) {
                return false;
            }

            // 获取mBase.mPackageInfo
            // 1. ApplicationContext - Android 2.1
            // 2. ContextImpl - Android 2.2 and higher
            // 3. AppContextImpl - Android 2.2 and higher
            Object oPackageInfo = ReflectUtils.readField(oBase, "mPackageInfo");
            if (oPackageInfo == null) {
                return false;
            }
            String fieldName = "mLibDir";
            String libDir = (String) ReflectUtils.readField(oPackageInfo,fieldName);
            Log.d(TAG, "libDr " +libDir);

            // baidumapsdk.demo.ReflectUtils.writeField(oPackageInfo,);
            // mPackageInfo的类型主要有两种：
            // 1. android.app.ActivityThread$PackageInfo - Android 2.1 - 2.3
            // 2. android.app.LoadedApk - Android 2.3.3 and higher
            Log.d(TAG, "patch: mBase cl=" + oBase.getClass() + "; mPackageInfo cl=" + oPackageInfo.getClass());

            // 获取mPackageInfo.mClassLoader
            ClassLoader oClassLoader = (ClassLoader) ReflectUtils.readField(oPackageInfo, "mClassLoader");
            if (oClassLoader == null) {
                return false;
            }
          //  classLoader = oClassLoader;
            ShareLibraryLoader.installNativeLibraryPath(oClassLoader,dir);
            // oClassLoader
            // 外界可自定义ClassLoader的实现，但一定要基于RePluginClassLoader类
            // ClassLoader cl = RePlugin.getConfig().getCallbacks().createClassLoader(oClassLoader.getParent(), oClassLoader);

            // 将新的ClassLoader写入mPackageInfo.mClassLoader
            // baidumapsdk.demo.ReflectUtils.writeField(oPackageInfo, "mClassLoader", cl);

            // 设置线程上下文中的ClassLoader为RePluginClassLoader
            // 防止在个别Java库用到了Thread.currentThread().getContextClassLoader()时，“用了原来的PathClassLoader”，或为空指针
            // Thread.currentThread().setContextClassLoader(oClassLoader);

            Log.d(TAG, "patch: patch mClassLoader ok");

        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

}