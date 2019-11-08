package baidumapsdk.demo;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

/**
 * Copycat from tinker's TinkerLoadLibrary#installNativeLibraryPath
 */
@SuppressLint("LongLogTag")
public class ShareLibraryLoader {
    private static final String TAG = "Sodler.ShareLibraryLoader";

    /**
     * All version of install logic obey the following strategies:
     * 1. If path of {@code folder} is not injected into the classloader, inject it to the
     * beginning of pathList in the classloader.
     * <p>
     * 2. Otherwise remove path of {@code folder} first, then re-inject it to the
     * beginning of pathList in the classloader.
     */
    public static void installNativeLibraryPath(ClassLoader classLoader, File folder)
            throws Throwable {
        if (folder == null || !folder.exists()) {
            Log.e(TAG, String.format("installNativeLibraryPath, folder %s is illegal", folder));
            return;
        }
        // android o sdk_int 26
        // for android o preview sdk_int 25
        if ((Build.VERSION.SDK_INT == 25 && Build.VERSION.PREVIEW_SDK_INT != 0)
                || Build.VERSION.SDK_INT > 25) {
            try {
                V25.install(classLoader, folder);
            } catch (Throwable throwable) {
                // install fail, try to treat it as v23
                // some preview N version may go here
                Log.e(TAG, String.format(
                        "installNativeLibraryPath, v25 fail, sdk: %d, error: %s, try to fallback to V23",
                        Build.VERSION.SDK_INT, throwable.getMessage()
                ));
                V23.install(classLoader, folder);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            try {
                V23.install(classLoader, folder);
            } catch (Throwable throwable) {
                // install fail, try to treat it as v14
                Log.e(TAG, String.format(
                        "installNativeLibraryPath, v23 fail, sdk: %d, error: %s, try to fallback to V14",
                        Build.VERSION.SDK_INT, throwable.getMessage()
                ));

                V14.install(classLoader, folder);
            }
        } else if (Build.VERSION.SDK_INT >= 14) {
            V14.install(classLoader, folder);
        } else {
            V4.install(classLoader, folder);
        }
    }

    private static final class V4 {
        private static void install(ClassLoader classLoader, File folder) throws Throwable {
            String addPath = folder.getPath();
            Field pathField = ShareReflectUtil.findField(classLoader, "libPath");
            String orgLibPath = String.valueOf(pathField.get(classLoader));
            String newLibPath = addPath;
            if (!TextUtils.isEmpty(orgLibPath)) {
                newLibPath = orgLibPath + ":" + addPath;
            }
            pathField.set(classLoader, newLibPath);

            final Field libraryPathElementsFiled = ShareReflectUtil.findField(classLoader, "libraryPathElements");
            @SuppressWarnings("unchecked") final List<String> libraryPathElements = (List<String>) libraryPathElementsFiled.get(classLoader);
            final Iterator<String> libPathElementIt = libraryPathElements.iterator();
            while (libPathElementIt.hasNext()) {
                final String libPath = libPathElementIt.next();
                if (addPath.equals(libPath)) {
                    libPathElementIt.remove();
                    break;
                }
            }
            libraryPathElements.add(addPath);
            libraryPathElementsFiled.set(classLoader, libraryPathElements);
        }
    }

    private static final class V14 {
        private static void install(ClassLoader classLoader, File folder) throws Throwable {
            final Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
            final Object dexPathList = pathListField.get(classLoader);

            final Field nativeLibDirField = ShareReflectUtil.findField(dexPathList, "nativeLibraryDirectories");
            final File[] origNativeLibDirs = (File[]) nativeLibDirField.get(dexPathList);

            final List<File> newNativeLibDirList = new ArrayList<>();
            for (File origNativeLibDir : origNativeLibDirs) {
                if (!folder.equals(origNativeLibDir)) {
                    newNativeLibDirList.add(origNativeLibDir);
                }
            }
            newNativeLibDirList.add(folder);
            nativeLibDirField.set(dexPathList, newNativeLibDirList.toArray(new File[0]));
        }
    }

    private static final class V23 {
        private static void install(ClassLoader classLoader, File folder) throws Throwable {
            final Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
            final Object dexPathList = pathListField.get(classLoader);

            final Field nativeLibraryDirectories = ShareReflectUtil.findField(dexPathList, "nativeLibraryDirectories");

            @SuppressWarnings("unchecked")
            List<File> origLibDirs = (List<File>) nativeLibraryDirectories.get(dexPathList);
            if (origLibDirs == null) {
                origLibDirs = new ArrayList<>(2);
            }
            final Iterator<File> libDirIt = origLibDirs.iterator();
            while (libDirIt.hasNext()) {
                final File libDir = libDirIt.next();
                if (folder.equals(libDir)) {
                    libDirIt.remove();
                    break;
                }
            }
            origLibDirs.add(folder);

            final Field systemNativeLibraryDirectories = ShareReflectUtil.findField(dexPathList, "systemNativeLibraryDirectories");
            @SuppressWarnings("unchecked")
            List<File> origSystemLibDirs = (List<File>) systemNativeLibraryDirectories.get(dexPathList);
            if (origSystemLibDirs == null) {
                origSystemLibDirs = new ArrayList<>(2);
            }

            final List<File> newLibDirs = new ArrayList<>(origLibDirs.size() + origSystemLibDirs.size() + 1);
            newLibDirs.addAll(origLibDirs);
            newLibDirs.addAll(origSystemLibDirs);

            final Method makeElements = ShareReflectUtil.findMethod(dexPathList, "makePathElements", List.class, File.class, List.class);
            final ArrayList<IOException> suppressedExceptions = new ArrayList<>();

            final Object[] elements = (Object[]) makeElements.invoke(dexPathList, newLibDirs, null, suppressedExceptions);

            final Field nativeLibraryPathElements = ShareReflectUtil.findField(dexPathList, "nativeLibraryPathElements");
            nativeLibraryPathElements.set(dexPathList, elements);
        }
    }

    private static final class V25 {
        private static void install(ClassLoader classLoader, File folder) throws Throwable {
            final Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
            final Object dexPathList = pathListField.get(classLoader);

            final Field nativeLibraryDirectories = ShareReflectUtil.findField(dexPathList, "nativeLibraryDirectories");

            @SuppressWarnings("unchecked")
            List<File> origLibDirs = (List<File>) nativeLibraryDirectories.get(dexPathList);
            if (origLibDirs == null) {
                origLibDirs = new ArrayList<>(2);
            }
            final Iterator<File> libDirIt = origLibDirs.iterator();
            while (libDirIt.hasNext()) {
                final File libDir = libDirIt.next();
                if (folder.equals(libDir)) {
                    libDirIt.remove();
                    break;
                }
            }
            origLibDirs.add(folder);

            final Field systemNativeLibraryDirectories = ShareReflectUtil.findField(dexPathList, "systemNativeLibraryDirectories");
            @SuppressWarnings("unchecked")
            List<File> origSystemLibDirs = (List<File>) systemNativeLibraryDirectories.get(dexPathList);
            if (origSystemLibDirs == null) {
                origSystemLibDirs = new ArrayList<>(2);
            }

            final List<File> newLibDirs = new ArrayList<>(origLibDirs.size() + origSystemLibDirs.size() + 1);
            newLibDirs.addAll(origLibDirs);
            newLibDirs.addAll(origSystemLibDirs);

            final Method makeElements = ShareReflectUtil.findMethod(dexPathList, "makePathElements", List.class);

            final Object[] elements = (Object[]) makeElements.invoke(dexPathList, newLibDirs);

            final Field nativeLibraryPathElements = ShareReflectUtil.findField(dexPathList, "nativeLibraryPathElements");
            nativeLibraryPathElements.set(dexPathList, elements);
        }
    }
}
