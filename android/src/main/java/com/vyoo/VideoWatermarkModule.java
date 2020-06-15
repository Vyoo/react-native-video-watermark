package me.vyoo;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import com.daasuu.mp4compose.composer.Mp4Composer;
import com.daasuu.mp4compose.filter.GlWatermarkFilter;
import com.daasuu.mp4compose.FillMode;
import com.vyoo.commonvideolibrary.SamplerClip;
import com.vyoo.commonvideolibrary.VideoResampler;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.net.Uri;
import android.util.Log;
import android.graphics.BitmapFactory;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

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

    File samplerDestFile = new File(this.getReactApplicationContext().getFilesDir(), "converted.mp4");
      if (!samplerDestFile.exists()) {
          try {
              samplerDestFile.createNewFile();
          } catch (IOException e) {
              e.printStackTrace();
          }
      }
      final String samplerDestinationPath = samplerDestFile.getPath();

      VideoResampler resampler = new VideoResampler();

      resampler.addSamplerClip(new SamplerClip(Uri.parse(videoPath)));
      resampler.setOutput(Uri.parse(samplerDestinationPath));
      resampler.setOutputResolution(width, height);
      try {
          resampler.start();
      } catch (Throwable e) {
          e.printStackTrace();
      }
      //successCallback.invoke(destinationPath);
      File destFile = new File(this.getReactApplicationContext().getFilesDir(), "converted1.mp4");
      if (!destFile.exists()) {
          try {
              destFile.createNewFile();
          } catch (IOException e) {
              e.printStackTrace();
          }
      }
      final String destinationPath = destFile.getPath();
      try {
          new Mp4Composer(Uri.fromFile(samplerDestFile), destinationPath, reactContext)
                  .filter(new GlWatermarkFilter(BitmapFactory.decodeStream(reactContext.getContentResolver().openInputStream(Uri.fromFile(new File(imagePath))))))                  
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
                          exception.printStackTrace();
                      }
                  }).start();
      } catch (FileNotFoundException e) {
          e.printStackTrace();
      }
  }
}