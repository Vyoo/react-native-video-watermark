package me.vyoo;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import com.daasuu.mp4compose.composer.Mp4Composer;
import com.daasuu.mp4compose.filter.GlWatermarkFilter;
import com.daasuu.mp4compose.FillMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;
import android.graphics.BitmapFactory;

public class VideoWatermarkModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;

  public VideoWatermarkModule(ReactApplicationContext reactContext) {
      super(reactContext);
      this.reactContext = reactContext;
  }

  @Override
  public String getName() {
      return "VideoWatermark";
  }

  @ReactMethod
  public void convert(String videoPath, String imagePath, int width, int height, Callback successCallback, Callback failureCallback) {
      watermarkVideoWithImage(videoPath, imagePath, width, height, successCallback, failureCallback);
  }

  public void watermarkVideoWithImage(String videoPath, String imagePath, int width, int height, final Callback successCallback, final Callback failureCallback) {
      int videoWidth = 0;
      int videoHeight = 0;
    File destFile = new File(this.getReactApplicationContext().getFilesDir(), "converted.mp4");    
      if (!destFile.exists()) {
          try {
              destFile.createNewFile();
              videoWidth = this.getVideoWidthOrHeight(new File(videoPath), "width");
              videoHeight = this.getVideoWidthOrHeight(new File(videoPath), "hieght");
          } catch (IOException e) {
              e.printStackTrace();
          }
      }

      final String destinationPath = destFile.getPath();
      final Bitmap resizedImage = this.resizeBitmapImage(imagePath, videoWidth, videoHeight);
      new Mp4Composer(Uri.fromFile(new File(videoPath)), destinationPath, reactContext)
              .filter(new GlWatermarkFilter(resizedImage))
              .listener(new Mp4Composer.Listener() {
                  @Override
                  public void onProgress(double progress) {
                      Log.e("Progress", progress + "");
                  }
                  @Override
                  public void onCompleted() {
                      successCallback.invoke(destinationPath);
                  }

                  @Override
                  public void onCanceled() {
                      //Log.e("Progress", "Cancelled" + "");
                      failureCallback.invoke("cancelled");
                  }

                  @Override
                  public void onFailed(Exception exception) {
                      Log.e("Progress", "Failure" + "");
                      failureCallback.invoke("failed");
                      //exception.printStackTrace();
                  }
              }).start();
  }
    public Bitmap resizeBitmapImage(String photoPath, int targetW, int targetH) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(photoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        int scaleFactor = 1;
        if ((targetW > 0) || (targetH > 0)) {
            scaleFactor = Math.min(photoW/targetW, photoH/targetH);
        }

        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
//        bmOptions.inPurgeable = true; //Deprecated API 21

        return BitmapFactory.decodeFile(photoPath, bmOptions);
    }

    public int getVideoWidthOrHeight(File file, String widthOrHeight) throws IOException {
        MediaMetadataRetriever retriever = null;
        Bitmap bmp = null;
        FileInputStream inputStream = null;
        int mWidthHeight = 0;
        try {
            retriever = new  MediaMetadataRetriever();
            inputStream = new FileInputStream(file.getAbsolutePath());
            retriever.setDataSource(inputStream.getFD());
            bmp = retriever.getFrameAtTime();
            if (widthOrHeight.equals("width")){
                mWidthHeight = bmp.getWidth();
            }else {
                mWidthHeight = bmp.getHeight();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally{
            if (retriever != null){
                retriever.release();
            }if (inputStream != null){
                inputStream.close();
            }
        }
        return mWidthHeight;
    }
}