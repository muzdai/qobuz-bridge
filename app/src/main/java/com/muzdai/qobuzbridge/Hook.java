package com.muzdai.qobuzbridge;

import android.app.Application;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Inside Qobuz (scope com.qobuz.music, main process): finds Qobuz's queue controller (internally "MediaQueueController")
 * and its "prepare and play" method, keeps the controller instance Qobuz creates, and takes broadcasts from vPro TV
 * Launcher to play tracks by their catalogue ids.
 *
 * Broadcast (explicit, package com.qobuz.music), only from com.muzdai.launcher (sender identity, Android 14):
 *   action com.muzdai.qobuzbridge.PLAY, extras "tracks" (JSON array, see QueueItems), "start" (index), "query";
 *   action com.muzdai.qobuzbridge.PING.
 * Result code: 1 done, 0 not ready (Qobuz has not built its player yet), -1 refused or failed; result data says why.
 */
public class Hook implements IXposedHookLoadPackage {
    static final String TAG = "QobuzBridge: ";
    static final String QOBUZ = "com.qobuz.music";
    static final String LAUNCHER = "com.muzdai.launcher";
    static final String ACTION_PLAY = "com.muzdai.qobuzbridge.PLAY";
    static final String ACTION_PING = "com.muzdai.qobuzbridge.PING";
    static final String VERSION = "1.0.1";

    /** Logged by the controller's "replace the queue, start at index, play" method, and nowhere else. */
    static final String PLAY_LOG =
            ", startPositionMs=0, repeatMode=null, shuffleModeEnabled=false, autoPlay=true, mediaItems=";
    static final String ITEM_CLASS = "com.qobuz.android.media.domain.model.player.state.queue.MediaQueueItem";

    private static Method play;
    private static Constructor<?> item;
    private static volatile WeakReference<Object> controller;
    private static boolean registered;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!QOBUZ.equals(lp.packageName) || !QOBUZ.equals(lp.processName)) return;
        try {
            find(lp);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "not active, Qobuz's player was not found: " + t);
            return;
        }
        // The controller is created by Qobuz's dependency injection: keep the newest one.
        XposedBridge.hookAllConstructors(play.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                controller = new WeakReference<>(param.thisObject);
            }
        });
        XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        register((Application) param.args[0]);
                    }
                });
        XposedBridge.log(TAG + VERSION + " ready: " + play.getDeclaringClass().getName() + "." + play.getName());
    }

    /** The play method by the string it logs (DexKit), and MediaQueueItem's 26-argument constructor. */
    private static void find(XC_LoadPackage.LoadPackageParam lp) throws Exception {
        System.loadLibrary("dexkit");
        try (DexKitBridge bridge = DexKitBridge.create(lp.appInfo.sourceDir)) {
            MethodDataList found = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings(PLAY_LOG)));
            if (found.size() != 1) throw new IllegalStateException(found.size() + " play methods");
            MethodData m = found.get(0);
            play = m.getMethodInstance(lp.classLoader);
            play.setAccessible(true);
        }
        for (Constructor<?> c : lp.classLoader.loadClass(ITEM_CLASS).getDeclaredConstructors()) {
            if (c.getParameterCount() == QueueItems.ARGS) item = c;
        }
        if (item == null) throw new IllegalStateException("MediaQueueItem changed");
        item.setAccessible(true);
    }

    // Before Android 13 there are no export flags; the receiver is exported on purpose (the launcher is another app).
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void register(Application app) {
        if (registered) return;
        registered = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PING);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                handle(this, intent);
            }
        };
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else app.registerReceiver(receiver, filter);
    }

    /** Runs on Qobuz's main thread, as its own screens call the controller. */
    private static void handle(BroadcastReceiver r, Intent intent) {
        if (Build.VERSION.SDK_INT >= 34 && !LAUNCHER.equals(r.getSentFromPackage())) {
            r.setResult(-1, "only " + LAUNCHER + " may send this", null);
            return;
        }
        Object c = controller == null ? null : controller.get();
        if (ACTION_PING.equals(intent.getAction())) {
            r.setResult(c != null ? 1 : 0, VERSION, null);
            return;
        }
        if (c == null) {
            r.setResult(0, "Qobuz's player is not built yet", null);
            return;
        }
        try {
            List<Object> items = new ArrayList<>();
            for (Object[] args : QueueItems.fromJson(intent.getStringExtra("tracks"), intent.getStringExtra("query"))) {
                items.add(item.newInstance(args));
            }
            if (items.isEmpty()) {
                r.setResult(-1, "no tracks", null);
                return;
            }
            int start = Math.max(0, Math.min(items.size() - 1, intent.getIntExtra("start", 0)));
            play.invoke(c, start, items);
            r.setResult(1, "playing " + items.size(), null);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "play failed: " + t);
            r.setResult(-1, String.valueOf(t), null);
        }
    }
}
