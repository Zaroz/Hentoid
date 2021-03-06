package me.devsaki.hentoid.util;

import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by avluis on 09/11/2016.
 * Zip Utility
 */

class ZipUtil {
    private static final String TAG = LogHelper.makeLogTag(ZipUtil.class);

    private static final int BUFFER = 32 * 1024;

    private static void add(final File file, final ZipOutputStream stream, final byte[] data) {
        LogHelper.d(TAG, "Adding: " + file);
        BufferedInputStream origin;
        try {
            FileInputStream fi = new FileInputStream(file);
            origin = new BufferedInputStream(fi, BUFFER);

            ZipEntry zipEntry = new ZipEntry(file.getName());
            stream.putNextEntry(zipEntry);
            int count;

            while ((count = origin.read(data, 0, BUFFER)) != -1) {
                stream.write(data, 0, count);
            }
        } catch (FileNotFoundException e) {
            LogHelper.e(TAG, e, "File Not Found: " + file);
        } catch (IOException e) {
            LogHelper.d(TAG, e, "IO Exception");
        }
    }

    abstract static class ZipTask extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... params) {
            File[] files = (File[]) params[0];
            File dest = (File) params[1];
            FileOutputStream out = null;
            ZipOutputStream zipOutputStream = null;
            try {
                out = new FileOutputStream(dest);
                zipOutputStream = new ZipOutputStream(new BufferedOutputStream(out));
                final byte data[] = new byte[BUFFER];
                for (File file : files) {
                    add(file, zipOutputStream, data);
                }
                FileUtil.sync(out);
                out.flush();
            } catch (Exception e) {
                LogHelper.e(TAG, e, "Error");
                return false;
            } finally {
                if (zipOutputStream != null) {
                    try {
                        zipOutputStream.close();
                    } catch (IOException e) {
                        LogHelper.d(TAG, e, "IO Exception");
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        LogHelper.d(TAG, e, "IO Exception");
                    }
                }
            }

            return true;
        }
    }

    static class UnZipTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String filePath = params[0];
            String destinationPath = params[1];

            File archive = new File(filePath);
            try {
                ZipFile zipfile = new ZipFile(archive);
                for (Enumeration e = zipfile.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = (ZipEntry) e.nextElement();
                    unzipEntry(zipfile, entry, destinationPath);
                }
                zipfile.close();
            } catch (Exception e) {
                LogHelper.e(TAG, e, "Error while extracting file: " + archive);
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            LogHelper.d(TAG, "All files extracted without error: " + aBoolean);
        }

        private void unzipEntry(ZipFile zipfile, ZipEntry entry, String outputDir)
                throws IOException {

            if (entry.isDirectory()) {
                createDir(new File(outputDir, entry.getName()));
                return;
            }

            File outputFile = new File(outputDir, entry.getName());
            if (!outputFile.getParentFile().exists()) {
                createDir(outputFile.getParentFile());
            }

            BufferedInputStream in = new BufferedInputStream(zipfile.getInputStream(entry));
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile));
            try {
                byte[] buffer = new byte[BUFFER];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                FileHelper.sync(out);
                out.flush();
            } finally {
                try {
                    out.close();
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        private void createDir(File dir) {
            if (dir.exists()) {
                return;
            }
            LogHelper.d(TAG, "Creating dir: " + dir.getName());
            if (!dir.mkdirs()) {
                LogHelper.w(TAG, "Could not create dir: " + dir);
            }
        }
    }
}
