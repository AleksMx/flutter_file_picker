package com.mr.flutter.plugin.filepicker;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Random;

public class FileUtils {

    private static final String TAG = "FilePickerUtils";
    private static final String PRIMARY_VOLUME_NAME = "primary";

    public static String[] getMimeTypes(final ArrayList<String> allowedExtensions) {

        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return null;
        }

        final ArrayList<String> mimes = new ArrayList<>();

        for (int i = 0; i < allowedExtensions.size(); i++) {
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(allowedExtensions.get(i));
            if (mime == null) {
                Log.w(TAG, "Custom file type " + allowedExtensions.get(i) + " is unsupported and will be ignored.");
                continue;
            }

            mimes.add(mime);
        }
        Log.d(TAG, "Allowed file extensions mimes: " + mimes);
        return mimes.toArray(new String[0]);
    }

    public static String getFileName(Uri uri, final Context context) {
        String result = null;

        //if uri is content
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    //local filesystem
                    int index = cursor.getColumnIndex("_data");
                    if (index == -1)
                    //google drive
                    {
                        index = cursor.getColumnIndex("_display_name");
                    }
                    result = cursor.getString(index);
                    if (result != null) {
                        uri = Uri.parse(result);
                    } else {
                        return null;
                    }
                }
            } catch (final Exception ex) {
                Log.e(TAG, "Failed to decode file name: " + ex.toString());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        if (uri.getPath() != null) {
            result = uri.getPath();
            final int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }

        return result;
    }

    public static boolean clearCache(final Context context) {
        try {
            final File cacheDir = new File(context.getCacheDir() + "/file_picker/");
            final File[] files = cacheDir.listFiles();

            if (files != null) {
                for (final File file : files) {
                    file.delete();
                }
            }
        } catch (final Exception ex) {
            Log.e(TAG, "There was an error while clearing cached files: " + ex.toString());
            return false;
        }
        return true;
    }

    public static FileInfo openFileStream(final Context context, final Uri uri, boolean withData) {
        boolean notCache = true;
        if (notCache && withData) {
            try {
                final InputStream inputStream = context.getContentResolver().openInputStream(uri);

                Log.i(TAG, "Read without cache: " + uri.toString());

                int size = (int) inputStream.available();
                byte[] bytes = new byte[size];

                int bytesReaded = 0;
                int readSize = 4096 * 10;
                while (inputStream.available() > 0) {
                    int toRead = inputStream.available();
                    if (toRead > readSize) {
                        toRead = readSize;
                    }
                    int count = inputStream.read(bytes, bytesReaded, toRead);
                    bytesReaded += count;
                }
                inputStream.close();

                final FileInfo.Builder fileInfo = new FileInfo.Builder();
                final String fileName = FileUtils.getFileName(uri, context);

                fileInfo
                    .withPath(uri.toString())
                    .withName(fileName)
                    .withData(bytes)
                    .withSize(Integer.parseInt(String.valueOf(size/1024)));

                return fileInfo.build();
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File not found: " + e.getMessage(), null);
            } catch (IOException e) {
                Log.e(TAG, "Failed to close file streams: " + e.getMessage(), null);
            }
            return null;
        }


        Log.i(TAG, "Caching from URI: " + uri.toString());
        final FileInfo.Builder fileInfo = new FileInfo.Builder();
        final String fileName = FileUtils.getFileName(uri, context);
        final String path = context.getCacheDir().getAbsolutePath() + "/file_picker/" + (fileName != null ? fileName : new Random().nextInt(100000));

        final File file = new File(path);

        if(file.exists() && withData) {
            Log.i(TAG, "read cache");

            int size = (int) file.length();
            byte[] bytes = new byte[size];

            try {
                BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                Log.i(TAG, "read file: " + uri.toString());

                int bytesReaded = 0;
                while (inputStream.available() > 0) {
                    int toRead = inputStream.available();
                    if (toRead > 4096) {
                        toRead = 4096;
                    }
                    int count = inputStream.read(bytes, bytesReaded, toRead);
                    bytesReaded += count;
                }

                inputStream.close();
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File not found: " + e.getMessage(), null);
            } catch (IOException e) {
                Log.e(TAG, "Failed to close file streams: " + e.getMessage(), null);
            }
            fileInfo.withData(bytes);
        } else {
            Log.i(TAG, "no read file");
            file.getParentFile().mkdirs();
            try {
                final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(path));
                final InputStream in = context.getContentResolver().openInputStream(uri);

                final byte[] buffer = new byte[8192];
                int len = 0;
                while ((len = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, len);
                }
                in.close();
                out.close();

                //
                if(withData) {
                     Log.i(TAG, "withData");
                    try {
                        byte[] bytes = new byte[(int) file.length()];

                        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                        Log.i(TAG, "read file: " + uri.toString());

                        int bytesReaded = 0;
                        while (inputStream.available() > 0) {
                            int toRead = inputStream.available();
                            if (toRead > 4096) {
                                toRead = 4096;
                            }
                            int count = inputStream.read(bytes, bytesReaded, toRead);
                            bytesReaded += count;
                        }

                        Log.i(TAG, "inputStream readed");

                        inputStream.close();

                        fileInfo.withData(bytes);
                    }  catch (Exception e) {
                        Log.e(TAG, "Failed to load bytes into memory with error " + e.toString() + ". Probably the file is too big to fit device memory. Bytes won't be added to the file this time.");
                    }
                }
            } catch (final Exception e) {
                Log.e(TAG, "Failed to retrieve path: " + e.getMessage(), null);
                return null;
            }
        }

        Log.d(TAG, "File loaded and cached at:" + path);

        fileInfo
                .withPath(path)
                .withName(fileName)
                .withSize(Integer.parseInt(String.valueOf(file.length()/1024)));

        return fileInfo.build();
    }

    @Nullable
    public static String getFullPathFromTreeUri(@Nullable final Uri treeUri, Context con) {
        if (treeUri == null) {
            return null;
        }

        String volumePath = getVolumePath(getVolumeIdFromTreeUri(treeUri), con);
        FileInfo.Builder fileInfo = new FileInfo.Builder();

        if (volumePath == null) {
            return File.separator;
        }

        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);

        String documentPath = getDocumentPathFromTreeUri(treeUri);

        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length() - 1);

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator)) {
                return volumePath + documentPath;
            }
            else {
                return volumePath + File.separator + documentPath;
            }
        } else {
            return volumePath;
        }
    }


    @SuppressLint("ObsoleteSdkInt")
    private static String getVolumePath(final String volumeId, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null;
        try {
            StorageManager mStorageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);
                Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

                // primary volume?
                if (primary && PRIMARY_VOLUME_NAME.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);

                // other volumes?
                if (uuid != null && uuid.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);
            }
            // not found.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }

}