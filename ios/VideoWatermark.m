#import "VideoWatermark.h"
#import <AVFoundation/AVFoundation.h>
#import <Photos/Photos.h>
@implementation VideoWatermark

RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(convert:(NSString *)videoUri imageUri:(nonnull NSString *)imageUri width:(int )width height:(int )height callback:(RCTResponseSenderBlock)callback failureCallback:(RCTResponseSenderBlock)failureCallback)
{
    [self watermarkVideoWithImage:videoUri imageUri:imageUri callback:callback failureCallback:failureCallback];
}

-(void)watermarkVideoWithImage:(NSString *)videoUri imageUri:(NSString *)imageUri callback:(RCTResponseSenderBlock)callback failureCallback:(RCTResponseSenderBlock)failureCallback
{
    AVAsset* videoAsset = [[AVURLAsset alloc]initWithURL:[NSURL fileURLWithPath:videoUri] options:nil];    
    BOOL isTrackAvailable = [[videoAsset tracksWithMediaType:AVMediaTypeVideo] count] > 0;
    if (isTrackAvailable) {
        [self processedVideo:videoAsset imageUri:imageUri callback:callback];
    } else {
        PHFetchResult *phAssetFetchResult = [PHAsset fetchAssetsWithALAssetURLs:@[[NSURL URLWithString:videoUri]] options:nil];
        PHAsset *phAsset = [phAssetFetchResult firstObject];
        dispatch_group_t group = dispatch_group_create();
        dispatch_group_enter(group);
        [[PHImageManager defaultManager] requestAVAssetForVideo:phAsset options:nil resultHandler:^(AVAsset * _Nullable asset, AVAudioMix * _Nullable audioMix, NSDictionary * _Nullable info) {
            [self processedVideo:asset imageUri:imageUri callback:callback];
        }];
    }
}

- (void) processedVideo:(AVAsset *)videoAsset  imageUri:(NSString *)imageUri callback:(RCTResponseSenderBlock)callback{
    AVMutableComposition* mixComposition = [AVMutableComposition composition];
    AVMutableCompositionTrack *compositionVideoTrack = [mixComposition addMutableTrackWithMediaType:AVMediaTypeVideo preferredTrackID:kCMPersistentTrackID_Invalid];
    AVAssetTrack *clipVideoTrack = [[videoAsset tracksWithMediaType:AVMediaTypeVideo] objectAtIndex:0];
    AVMutableCompositionTrack *compositionAudioTrack = [mixComposition addMutableTrackWithMediaType:AVMediaTypeAudio preferredTrackID:kCMPersistentTrackID_Invalid];
    AVAssetTrack *clipAudioTrack = [[videoAsset tracksWithMediaType:AVMediaTypeAudio] objectAtIndex:0];
    [compositionVideoTrack insertTimeRange:CMTimeRangeMake(kCMTimeZero, videoAsset.duration) ofTrack:clipVideoTrack atTime:kCMTimeZero error:nil];
    [compositionAudioTrack insertTimeRange:CMTimeRangeMake(kCMTimeZero, videoAsset.duration) ofTrack:clipAudioTrack atTime:kCMTimeZero error:nil];
    [compositionVideoTrack setPreferredTransform:[[[videoAsset tracksWithMediaType:AVMediaTypeVideo] objectAtIndex:0] preferredTransform]];
    UIImage *myImage=[UIImage imageWithContentsOfFile:imageUri];
    CGSize sizeOfVideo = myImage.size;// CGSizeApplyAffineTransform(myImage.size, clipVideoTrack.preferredTransform);
    sizeOfVideo.width = fabs(sizeOfVideo.width);
    
    //Image of watermark
    
    
    UIGraphicsBeginImageContext(myImage.size);
    [myImage drawInRect:CGRectMake(0, 0, sizeOfVideo.width, sizeOfVideo.height)];
    UIImage *destImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    myImage = destImage;
    
    
    CALayer *layerCa = [CALayer layer];
    layerCa.contents = (id)myImage.CGImage;
    layerCa.frame = CGRectMake(0, 0, sizeOfVideo.width, sizeOfVideo.height);
    layerCa.opacity = 1.0;
    
    CALayer *parentLayer=[CALayer layer];
    CALayer *videoLayer=[CALayer layer];
    parentLayer.frame=CGRectMake(0, 0, sizeOfVideo.width, sizeOfVideo.height);
    videoLayer.frame=CGRectMake(0, 0, sizeOfVideo.width, sizeOfVideo.height);
    [parentLayer addSublayer:videoLayer];
    [parentLayer addSublayer:layerCa];
    
    AVMutableVideoComposition *videoComposition=[AVMutableVideoComposition videoComposition] ;
    videoComposition.frameDuration=CMTimeMake(1, 30);
    videoComposition.renderSize=sizeOfVideo;
    videoComposition.animationTool=[AVVideoCompositionCoreAnimationTool videoCompositionCoreAnimationToolWithPostProcessingAsVideoLayer:videoLayer inLayer:parentLayer];
    
    AVMutableVideoCompositionInstruction *instruction = [AVMutableVideoCompositionInstruction videoCompositionInstruction];
    instruction.timeRange = CMTimeRangeMake(kCMTimeZero, [mixComposition duration]);
    AVAssetTrack *videoTrack = [[mixComposition tracksWithMediaType:AVMediaTypeVideo] objectAtIndex:0];
    
    
    AVMutableVideoCompositionLayerInstruction* layerInstruction = [AVMutableVideoCompositionLayerInstruction videoCompositionLayerInstructionWithAssetTrack:videoTrack];
    
    
    // https://stackoverflow.com/questions/44911802/video-rotated-after-applying-avvideocomposition/45058026
    BOOL  isAssetPortrait_  = NO;
    CGAffineTransform trackTransform = clipVideoTrack.preferredTransform;
    if(trackTransform.a == 0 && trackTransform.b == 1.0 && trackTransform.c == -1.0 && trackTransform.d == 0)  {
        isAssetPortrait_ = YES;
    }
    if(trackTransform.a == 0 && trackTransform.b == -1.0 && trackTransform.c == 1.0 && trackTransform.d == 0)  {
        isAssetPortrait_ = YES;
    }
    if(isAssetPortrait_){
        CGAffineTransform assetScaleFactor = CGAffineTransformMakeScale(1.0, 1.0);
        [layerInstruction setTransform:CGAffineTransformConcat(clipVideoTrack.preferredTransform, assetScaleFactor) atTime:kCMTimeZero];
    }
    
    
    instruction.layerInstructions = [NSArray arrayWithObject:layerInstruction];
    videoComposition.instructions = [NSArray arrayWithObject: instruction];
    
    NSString *documentsDirectory = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)objectAtIndex:0];
    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    [dateFormatter setDateFormat:@"yyyy-MM-dd_HH-mm-ss"];
    NSString *destinationPath = [documentsDirectory stringByAppendingFormat:@"/output_%@.mov", [dateFormatter stringFromDate:[NSDate date]]];
    
    AVAssetExportSession *exportSession = [[AVAssetExportSession alloc] initWithAsset:mixComposition presetName:AVAssetExportPresetHighestQuality];
    exportSession.videoComposition=videoComposition;
    
    exportSession.outputURL = [NSURL fileURLWithPath:destinationPath];
    exportSession.outputFileType = AVFileTypeQuickTimeMovie;
    [exportSession exportAsynchronouslyWithCompletionHandler:^{
        switch (exportSession.status)
        {
            case AVAssetExportSessionStatusCompleted:
                NSLog(@"Export OK");
                callback(@[destinationPath]);
                break;
            case AVAssetExportSessionStatusFailed:
                NSLog (@"AVAssetExportSessionStatusFailed: %@", exportSession.error);
                break;
            case AVAssetExportSessionStatusCancelled:
                NSLog(@"Export Cancelled");
                break;
        }
    }];
}


@end
