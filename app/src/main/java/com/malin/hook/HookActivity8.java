package com.malin.hook;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

/**
 * malin
 * 反射
 * https://www.cnblogs.com/chanshuyi/p/head_first_of_reflection.html
 * https://blog.csdn.net/jiangwei0910410003/article/details/52550147
 * >=android 8.0以上的Hook
 */
@SuppressWarnings("JavaReflectionMemberAccess")
@SuppressLint("PrivateApi")
public class HookActivity8 {
    private static final String TAG = "HookActivityUtils8";
    private static final String EXTRA_ORIGIN_INTENT = "EXTRA_ORIGIN_INTENT";


    public static void hookStartActivity(Context context, Class<?> aClass) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {

        //1.获取ActivityManager的Class对象
        //package android.app
        //public class ActivityManager
        Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");

        //2.获取ActivityManager的私有属性IActivityManagerSingleton
        //private static final Singleton<IActivityManager> IActivityManagerSingleton
        Field singletonIActivityManagerField = activityManagerClass.getDeclaredField("IActivityManagerSingleton");
        singletonIActivityManagerField.setAccessible(true);

        //3.Singleton<IActivityManager> IActivityManagerSingleton
        //所有静态对象的反射可以通过传null获取。如果是实列必须传实例
        Object IActivityManagerSingletonObj = singletonIActivityManagerField.get(null);


        //4.获取Singleton<IActivityManager> IActivityManagerSingleton对象中的属性private T mInstance;
        //既,为了获取一个IActivityManager的实例对象

        //5.拿到该属性

        //获取Singleton类对象
        //package android.util
        //public abstract class Singleton<T>
        Class<?> singletonClass = Class.forName("android.util.Singleton");

        //获取mInstance属性
        //private T mInstance;
        Field mInstanceField = singletonClass.getDeclaredField("mInstance");

        //设置不检查
        mInstanceField.setAccessible(true);

        //从Singleton<IActivityManager> IActivityManagerSingleton实例对象中获取mInstance属性对应的值,既IActivityManager
        Object iActivityManager = mInstanceField.get(IActivityManagerSingletonObj);


        //6.获取IActivityManager接口的类对象
        //package android.app
        //public interface IActivityManager

        Class<?> iActivityManagerClass = Class.forName("android.app.IActivityManager");

        Object iActivityManagerProxy = Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{iActivityManagerClass},
                new IActivityInvocationHandler(iActivityManager, context, aClass)
        );

        //7.重新赋值
        //给mInstance属性,赋新值
        //给Singleton<IActivityManager> IActivityManagerSingleton实例对象的属性private T mInstance赋新值
        mInstanceField.set(IActivityManagerSingletonObj, iActivityManagerProxy);


    }


    private static class IActivityInvocationHandler implements InvocationHandler {

        private Object mIActivityManager;
        private Class<?> mSubActivityClass;
        private Context mContext;


        public IActivityInvocationHandler(Object iActivityManager, Context context, Class<?> subActivityClass) {
            this.mIActivityManager = iActivityManager;
            this.mSubActivityClass = subActivityClass;
            this.mContext = context;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "invoke :" + method.getName() + " args:" + Arrays.toString(args));
            //public int startActivity(android.app.IApplicationThread caller, java.lang.String callingPackage, android.content.Intent intent, java.lang.String resolvedType, android.os.IBinder resultTo, java.lang.String resultWho, int requestCode, int flags, android.app.ProfilerInfo profilerInfo, android.os.Bundle options) throws android.os.RemoteException;
            if (method.getName().equals("startActivity")) {
                Log.d(TAG, "startActivity hook");
                int intentIndex = 2;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Intent) {
                        intentIndex = i;
                        break;
                    }
                }
                //1.将未注册的Activity对应的Intent,改为安全的Intent,既在AndroidManifest.xml中配置了的Activity的Intent
                Intent originIntent = (Intent) args[intentIndex];

                Intent safeIntent = new Intent(mContext, mSubActivityClass);
                safeIntent.putExtra(EXTRA_ORIGIN_INTENT, originIntent);

                //2.替换到原来的Intent,欺骗AMS
                args[intentIndex] = safeIntent;

                //3.之后,在换回来,启动我们未在AndroidManifest.xml中配置的Activity
                //final H mH = new H();
                //hook Handler消息的处理,给Handler增加mCallback


            }
            return method.invoke(mIActivityManager, args);
        }
    }


    /**
     * 启动未注册的Activity
     *
     * @param context          context
     * @param subActivityClass 注册了的Activity的Class对象
     * @param isAppCompat      是否是AppCompatActivity的子类
     * @throws ClassNotFoundException classNotFoundException
     * @throws NoSuchFieldException   noSuchFieldException
     * @throws IllegalAccessException illegalAccessException
     */
    public static void hookLauncherActivity(Context context, Class<?> subActivityClass, boolean isAppCompat) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        //1.获取ActivityThread的Class对象
        //package android.app
        // public final class ActivityThread
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");

        //2.获取ActivityThread对象属性sCurrentActivityThread
        //private static volatile ActivityThread sCurrentActivityThread;
        Field sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread");
        sCurrentActivityThreadField.setAccessible(true);

        //3.获取ActivityThread的对象(sCurrentActivityThread的值)实例.被声明为静态的
        Object activityThreadObj = sCurrentActivityThreadField.get(null);

        //4.获取ActivityThread 的属性mH
        //final H mH = new H();
        Field mHField = activityThreadClass.getDeclaredField("mH");
        mHField.setAccessible(true);

        //5.获取mH的值
        //从ActivityThread实例中获取mH属性对应的值,既mH的值
        Object mHObj = mHField.get(activityThreadObj);


        //6.获取Handler的Class对象
        //package android.os
        //public class Handler
        Class<?> handlerClass = Class.forName("android.os.Handler");


        //7.获取mCallback属性
        //final Callback mCallback;
        Field mCallbackField = handlerClass.getDeclaredField("mCallback");
        mCallbackField.setAccessible(true);


        //8.给mH增加mCallback
        //给mH,既Handler的子类设置mCallback属性,提前对消息进行处理.
        if (Build.VERSION.SDK_INT >= 28) {
            //android 9.0
            mCallbackField.set(mHObj, new HandlerCallbackP(context, subActivityClass, isAppCompat));
        } else {
            mCallbackField.set(mHObj, new HandlerCallback(context, subActivityClass, isAppCompat));
        }
    }

    private static class HandlerCallback implements Handler.Callback {
        private Context context;
        private Class<?> subActivityClass;
        private boolean isAppCompat;

        public HandlerCallback(Context context, Class<?> subActivityClass, boolean isAppCompat) {
            this.context = context;
            this.subActivityClass = subActivityClass;
            this.isAppCompat = isAppCompat;
        }

        @Override
        public boolean handleMessage(Message msg) {
            handleLaunchActivity(msg, context, subActivityClass, isAppCompat);
            return false;
        }
    }


    private static void handleLaunchActivity(Message msg, Context context, Class<?> subActivityClass, boolean isAppCompat) {
        int LAUNCH_ACTIVITY = 100;
        try {
            //1.获取ActivityThread的内部类H的Class对象
            //package android.app
            //public final class ActivityThread
            //private class H extends Handler {}
            Class<?> hClass = Class.forName("android.app.ActivityThread$H");

            //2.public static final int LAUNCH_ACTIVITY = 100;
            Field launch_activity_field = hClass.getField("LAUNCH_ACTIVITY");

            //3.获取LAUNCH_ACTIVITY的值
            LAUNCH_ACTIVITY = (int) launch_activity_field.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (msg.what == LAUNCH_ACTIVITY) {
            //final ActivityClientRecord r = (ActivityClientRecord) msg.obj;
            //1.从msg中获取ActivityClientRecord对象
            Object activityClientRecordObj = msg.obj;

            try {
                //2.获取ActivityClientRecord的intent属性
                // Intent intent;
                Field safeIntentField = activityClientRecordObj.getClass().getDeclaredField("intent");
                safeIntentField.setAccessible(true);

                //3.获取ActivityClientRecord的intent属性的值,既安全的Intent
                Intent safeIntent = (Intent) safeIntentField.get(activityClientRecordObj);

                //4.获取原始的Intent
                Intent originIntent = safeIntent.getParcelableExtra(EXTRA_ORIGIN_INTENT);

                if (originIntent == null) return;

                //5.将安全的Intent,替换为原始的Intent
                //给ActivityClientRecord对象的intent属性,赋值为原始的Intent(originIntent)
                safeIntentField.set(activityClientRecordObj, originIntent);

                //6.处理未注册的Activity为AppCompatActivity类或者子类的情况
                try {
                    if (isAppCompat) {
                        hookPM(context, subActivityClass);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 对Android 9.0的处理
     * https://www.cnblogs.com/Jax/p/9521305.html
     */
    private static class HandlerCallbackP implements Handler.Callback {

        private Context context;
        private Class<?> subActivityClass;
        private boolean isAppCompat;

        public HandlerCallbackP(Context context, Class<?> subActivityClass, boolean isAppCompat) {
            this.context = context;
            this.subActivityClass = subActivityClass;
            this.isAppCompat = isAppCompat;
        }

        @Override
        public boolean handleMessage(Message msg) {
            //android.app.ActivityThread$H.EXECUTE_TRANSACTION = 159
            //android 9.0反射,Accessing hidden field Landroid/app/ActivityThread$H;->EXECUTE_TRANSACTION:I (dark greylist, reflection)
            //android9.0 深灰名单（dark greylist）则debug版本在会弹出dialog提示框，在release版本会有Toast提示，均提示为"Detected problems with API compatibility"
            if (msg.what == 159) {//直接写死,不反射了
                handleActivity(msg);
            }
            return false;
        }

        private void handleActivity(Message msg) {
            // 这里简单起见,直接取出TargetActivity;
            //final ClientTransaction transaction = (ClientTransaction) msg.obj;

            //1.获取ClientTransaction对象
            Object clientTransactionObj = msg.obj;

            try {
                //2.获取ClientTransaction中属性mActivityCallbacks的值
                //private List<ClientTransactionItem> mActivityCallbacks;
                Field mActivityCallbacksField = clientTransactionObj.getClass().getDeclaredField("mActivityCallbacks");
                mActivityCallbacksField.setAccessible(true);

                List<Object> mActivityCallbacks = (List<Object>) mActivityCallbacksField.get(clientTransactionObj);

                if (mActivityCallbacks.size() <= 0) return;
                String className = "android.app.servertransaction.LaunchActivityItem";
                if (className.equals(mActivityCallbacks.get(0).getClass().getCanonicalName())) {

                    //LaunchActivityItem
                    // public class LaunchActivityItem extends ClientTransactionItem
                    Object launchActivityItem = mActivityCallbacks.get(0);

                    //3.ClientTransactionItem的Class对象
                    //package android.app.servertransaction;
                    //public class LaunchActivityItem extends ClientTransactionItem
                    Class<?> launchActivityItemClass = Class.forName("android.app.servertransaction.LaunchActivityItem");

                    //4.ClientTransactionItem的mIntent属性的mIntent的Field
                    //private Intent mIntent;
                    Field mIntentField = launchActivityItemClass.getDeclaredField("mIntent");
                    mIntentField.setAccessible(true);

                    //5.获取mIntent属性的值,既桩Intent,安全的Intent
                    Intent safeIntent = (Intent) mIntentField.get(launchActivityItem);

                    //6.获取原始的Intent
                    Intent originIntent = safeIntent.getParcelableExtra(EXTRA_ORIGIN_INTENT);

                    //7.将原始的Intent,赋值给clientTransactionItem的mIntent属性
                    mIntentField.set(launchActivityItem, originIntent);

                    //8.处理未注册的Activity为AppCompatActivity类或者子类的情况
                    try {
                        if (isAppCompat) {
                            hookPM(context, subActivityClass);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理未注册的Activity为AppCompatActivity类或者子类的情况
     *
     * @param context          context
     * @param subActivityClass 注册了的Activity的Class对象
     * @throws ClassNotFoundException    classNotFoundException
     * @throws NoSuchFieldException      noSuchFieldException
     * @throws IllegalAccessException    illegalAccessException
     * @throws NoSuchMethodException     noSuchMethodException
     * @throws InvocationTargetException invocationTargetException
     */
    private static void hookPM(Context context, Class<?> subActivityClass) throws ClassNotFoundException, NoSuchFieldException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String appPackageName = getAppPackageName(context);
        String subActivityClassName = subActivityClass.getName();

        //1.获取ActivityThread的值
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Field sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread");
        sCurrentActivityThreadField.setAccessible(true);
        Object activityThread = sCurrentActivityThreadField.get(null);


        //2.Hook getPackageManager方法
        //public static IPackageManager getPackageManager() {}
        Method getPackageManagerMethod = activityThread.getClass().getDeclaredMethod("getPackageManager");

        //3.获取getPackageManager方法的返回值IPackageManager,使用activityThread对象的实例,调用getPackageManager()方法返回IPackageManager对象的实例
        Object iPackageManager = getPackageManagerMethod.invoke(activityThread);


        Class<?> iPackageManagerClass = Class.forName("android.content.pm.IPackageManager");

        Object iPackageManagerProxy = Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{iPackageManagerClass},
                new IPackageManagerHandler(iPackageManager, appPackageName, subActivityClassName));

        //4.获取 sPackageManager 属性的Field
        //static IPackageManager sPackageManager;
        Field iPackageManagerField = activityThread.getClass().getDeclaredField("sPackageManager");
        iPackageManagerField.setAccessible(true);

        //5.给ActivityThread的属性sPackageManager设置新的值
        //activityThread实例对象设置sPackageManager属性的值
        iPackageManagerField.set(activityThread, iPackageManagerProxy);
    }

    private static class IPackageManagerHandler implements InvocationHandler {
        private final String mAppPackageName;
        private final String mSubActivityClassName;
        private Object mIPackageManager;

        IPackageManagerHandler(Object iPackageManager, String appPackageName, String subActivityClassName) {
            this.mIPackageManager = iPackageManager;
            this.mAppPackageName = appPackageName;
            this.mSubActivityClassName = subActivityClassName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            //IPackageManager
            //public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName className, int flags, int userId) throws android.os.RemoteException;
            if (method.getName().equals("getActivityInfo")) {
                int index = 0;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof ComponentName) {
                        index = i;
                        break;
                    }
                }
                ComponentName componentName = new ComponentName(mAppPackageName, mSubActivityClassName);
                args[index] = componentName;
            }
            return method.invoke(mIPackageManager, args);
        }
    }

    /**
     * 获取包名
     */
    private static String getAppPackageName(Context context) {
        Context applicationContext = context.getApplicationContext();
        return applicationContext.getPackageName();
    }
}
