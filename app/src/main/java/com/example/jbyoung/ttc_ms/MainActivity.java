package com.example.jbyoung.ttc_ms;

/* Simple Time To Contact (TTC) Android implementation ---
 *
 * EE 693 B 2017 Spring Term
 *
 * Method based on
 *
 * Horn, B.K.P., Y. Fang and I. Masaki,
 * "Time to Contact Relative to a Planar Surface,"
 * IEEE Transactions on Intelligent Transportation Systems,
 *
 * see http://people.csail.mit.edu/bkph/articles/Time_To_Contact.pdf
 *
 * Final version with array clone and efficient copying
 * Adding multiscale
 */

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.abs;

// CLASS ----------------------------------------------------------------------

// NOTE: there are two nested subclasses: DrawOnTop and CameraPreview
// They are done as nested classes so they can access variables in MainActivity easily

public class MainActivity extends Activity {
    String TAG = "TTC";		// tag for logcat output
    String asterisks = " *******************************************"; // noticable marker
    protected static int nCam=0;				// first, back-facing camera
    protected static Camera mCamera = null;

    protected static CameraPreview mPreview;
    protected static DrawOnTop mDrawOnTop;

    private static boolean DBG = BuildConfig.DEBUG; // provide normal log output in debug version

    static int mCameraFrameRate=10;	// preferred frames per second

    static boolean adjustFrameRateFlag=false;
    static boolean setFocusModeFlag=true;	// allow  camera to autofocus
    static boolean adjustExposureCompensationFlag=false;
    static boolean adjustISOFlag=false;

    static boolean correctDriftFlag=true;	// correct for brightness drift in camera
    //    static boolean centerExtractFlag=true;	// use center of preview image (TODO: change this)
    static boolean centerExtractFlag=false;	// use center of preview image (TODO: change this)
    static boolean multiScaleFlag=true;

    static int ISO=100;	// preferred ISO sensitivity

    static int exposureCompensation, minExposureCompensation, maxExposureCompensation;
    static float exposureCompensationStep;

    static boolean showCameraInfoFlag=true;	// show camera details in getParameters()
//	static boolean showCameraInfoAllFlag=true;	// show details about *all* cameras

    //	static boolean stretchToScreenFlag=true;	// stretch preview to fit screen
    static boolean stretchToScreenFlag=false;

    static boolean histogramFlag=false;	// show brightness histogram in log (debugging)
    static boolean traceFlag=false;		// show details of computation (debugging)
    static boolean verboseFlag=false;	// show details in onDraw (debugging)

    static boolean usingCallBackBufferFlag=false;

    static final int transparency = 0xFF000000;	// opaque bits in ARGB

    //	Preferred preview image size - will select actual height and width in selectPreviewSize(...)
    static int mCameraHeight=720;	//px C fix
    //    static int mCameraHeight=768;	// preferred preview camera image height
    //	static int mCameraHeight=1080;	// preferred preview camera image height
    static int mCameraWidth=1280;	// preferred preview camera image width
//	static int mCameraWidth=1920;	// preferred preview camera image width

    int mImageHeight, mImageWidth;	// actual size of preview image - set in onPreviewFrame

    static int screenHeight;		// display screen size
    static int screenWidth;

    static double screenXdpi, screenYdpi;	// display screen DPI

    //	TODO: allow multiples scales, not just one block averaging step
//	final static int mBlock = 2;	// 2 x 2 size of blocks to average/sum over
    final static int mBlock = 4;	// 4 x 4 size of blocks to average/sum over

    //multi scale
//    final static int mBlockScales[] = {2, 4};
//    final static int mBlockScales[] = {2, 4, 8};
//    final static int mBlockScales[] = {2, 4, 8, 16};
    final static int mBlockScales[] = {2, 4, 8, 16, 32};

    final static int scales  = mBlockScales.length;
    static int scaleChoice;

    static int mBlockHeight;	// height of block averaged image
    static int mBlockWidth;		// width of block averaged image
    //multiscale
    static int mBhScales[] = new int[scales];	// height of block averaged image
    static int mBwScales[]= new int[scales];		// width of block averaged image

    static byte[] mRawYUVData;	// place for raw (YUV) camera image
    static int[] mRawRGBData;	// place for RGB (converted) camera image

    static int[] mBlockData=null;		// current frame
    static int[] mBlockData_Prev=null;	// previous frame
    //multiscale
    static int[][] mBlockDataScales= new int[scales][];		// current frame
    static int[][] mBlockDataScales_Prev=new int[scales][];	// previous frame

//    static double[][] mBlockDataScales= new int[scales][];		// current frame
//    static double[][] mBlockDataScales_Prev=new int[scales][];	// previous frame

    static int[] mBlockDisplayE=null;	// E in superimposed subimage
    static int[] mBlockDisplayEx=null;	// Ex
    static int[] mBlockDisplayEy=null;	// Ey
    static int[] mBlockDisplayEt=null;	// Et
    static int[] mBlockDisplayG=null;	// G
    static int[] mBlockDisplayGEt=null;	// G * Et

    static long mCurtime;	// current time
    static long mPretime;	// previous time

    // to reduce "noise" in output, average results from last few frames
    final static int mStack = 4;	// how many old values remembered

    static double[] aStack;	// remembered values of A
    static double[] bStack;	// remembered values of B
    static double[] cStack;	// remembered values of C
    static int[] iTStack;	// remembered values of delta T
    //multiscale
    static double[][] aStackScales;	// remembered values of A
    static double[][] bStackScales;	// remembered values of B
    static double[][] cStackScales;	// remembered values of C

    static int mCount = 0;	// frame count (so can avoid first frame)

    static Bitmap mBitmap;		// bitmap of preview image to be shown on screen
    static Bitmap mBitmapE;		// E bitmap
    static Bitmap mBitmapEx;	// Ex bitmap
    static Bitmap mBitmapEy;	// Ey bitmap
    static Bitmap mBitmapEt;	// Et bitmap
    static Bitmap mBitmapG;		// G  bitmap
    static Bitmap mBitmapGEt;	// G * Et bitmap

    static double[] xx;			// place for calculated A, B, C
    //multiscale
//    static double[] xx_scales;			// place for calculated A, B, C
    static double[][] xx_scales;			// place for calculated A, B, C
    static double[] xx_tmp;

    static double[] zz;			// place for averaged A, B, C
    //multiscale
    static double[][] zz_scales;			// place for averaged A, B, C
    static double[] crs;		// temp for vector calculations

    static double[][] AA;			// place for 3 x 3 matrix in LSQ solution
    static double[][] Mtranspose;	// temp  for 3 x 3 matrix transpose
    static double[] bb;				// place for 3-vector right-hand-side

    static int idT;			// delta T (between frames)
    static int zdT;

    boolean mFinished;		// set to false by CameraPreview creator
    // set to true by onPause() (and surfaceDestroyed)
    // (mostly just to avoid drawing when already paused)

    int Etlowcount=0;		// how many pixels below threshold on E_t

    //  Optional: dump useful info into the log
    static boolean bDisplayInfoFlag = false;	// show info about display in log file
    static boolean nCameraInfoFlag = false;		// show info about camera in log file

    @Override
    protected void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (DBG) Log.v(TAG, "onCreate"+asterisks);
        if (!checkCameraHardware(this)) {	// (need "context" as argument here)
            Log.e(TAG, "Device does not have a camera! Exiting"); // tablet perhaps ?
            Log.e(TAG, "finish()");
            finish();
        }
        if (DBG) Log.v(TAG, "Build.VERSION.SDK_INT " + Build.VERSION.SDK_INT);
        // Hide the window title.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // go full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // force landscape orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getPermissions();   // NOTE: can *not* assume we actually have permissions after this call

        //Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


//		optional dump of useful info into the log
        if (bDisplayInfoFlag) ExtraInfo.showDisplayInfo(this); // show some info about display
        if (nCameraInfoFlag) ExtraInfo.showCameraInfoAll(); // show some info about all cameras
    }

    // Because the CameraDevice object is not a shared resource,
    // it's very important to release it when the activity is paused.

    @Override
    protected void onPause ()
    {
        super.onPause();
        if (DBG) Log.v(TAG, "onPause" + asterisks);
        mFinished = true;
        if (mCamera != null) {
            releaseCamera(mCamera);
            mCamera = null;		// ?
            if (DBG) Log.i(TAG, "stopped preview and released camera");
        }
    }

    // which means the CameraDevice has to be (re-)opened when the activity is (re-)started

    @Override
    protected void onResume ()
    {
        super.onResume();
        if (DBG) Log.v(TAG, "onResume" + asterisks);
        // only if we have permission to use the camera
        if (bCameraPermissionGranted) {
            // Create our DrawOnTop instance
            mDrawOnTop = new DrawOnTop(this);
            if (mCamera != null) Log.e(TAG, "Camera already open?"); // should not happen
            mCamera = openCamera(nCam);			// grab camera here
            if (showCameraInfoFlag)				// dump info into log
                ExtraInfo.showCameraInfo(mCamera);	// show parameters before changes made
            setCameraInfo(mCamera, nCam);	// try to set some camera params
            if (showCameraInfoFlag)
                ExtraInfo.showCameraInfo(mCamera);	// show parameters after changes made
            // Create our Preview instance (which uses the DrawOnTop instance)
            mPreview = new CameraPreview(this, mCamera, mDrawOnTop);
            // Set the Preview as the "content" of our activity.
            setContentView(mPreview);
            addContentView(mDrawOnTop, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    protected void onDestroy ()
    {
        super.onDestroy();
        if (DBG) Log.v(TAG, "OnDestroy" + asterisks);
        if (mCamera != null) {
            releaseCamera(mCamera);  // just in case it hasn't been released yet...
            mCamera = null;
            if (DBG) Log.i(TAG, "stopped preview and released camera");
        }
    }

    // Check if this Android device actually has a camera!
    public boolean checkCameraHardware (Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    protected static Camera openCamera (int nCam) {    // open camera number nCam
        String TAG = "openCamera";
        Camera mCamera = null;
        try {
            mCamera = Camera.open(nCam);    // open specified camera
            if (DBG) Log.v(TAG, "opened camera " + nCam + " successfully");
        } catch (Exception e) {
            Log.e(TAG, "Attempt to open camera " + nCam + " failed " + e.getMessage());
//			Perhaps because permission lacking for camera use
//			Log.e(TAG, "finish()");
//			finish(); // non-static method cannot be accessed from...
            Log.e(TAG, "System.exit(0)");
            System.exit(0);
        }
        return mCamera;
    }

    protected static void releaseCamera (Camera mCamera)
    {
        String TAG="releaseCamera";
        if (mCamera != null){
            mCamera.setPreviewCallback(null); // cancel preview callbacks
            mCamera.stopPreview();		// stop previews
            mCamera.release();			// release the camera
            if (DBG) Log.v(TAG, "released successfully");
        }
        else Log.e(TAG, "Camera already released?"); // should not happen
    }

// Find a suitable preview size amongst those available

    public void selectPreviewSize (Camera mCamera, int k)
    {
        String TAG="selectPreviewSize";
        Camera.Parameters params = mCamera.getParameters();
//		list all available preview image sizes and pick one
        List<Camera.Size> cSizes = params.getSupportedPictureSizes();
        int difPixels, difMinPixels = -1;
        int nPixels = mCameraHeight * mCameraWidth;	// desired size
        if (k == nCam) Log.v(TAG, "Looking for about " + nPixels + " pixels");
        for (Camera.Size cSize : cSizes) {    // step through available camera image sizes
            if (DBG) Log.i(TAG, "Size " + cSize.height + " x " + cSize.width);
            if (k == nCam) {    // for chosen camera only
                // use desired pixel count as a guide to selection
                difPixels = abs(cSize.height * cSize.width - nPixels);
                if (difMinPixels < 0 || difPixels < difMinPixels) {
                    mCameraHeight = cSize.height;
                    mCameraWidth = cSize.width;
                    difMinPixels = difPixels;
                }
            }
        }
        if (k == nCam) // for chosen camera only
            Log.v(TAG, "Nearest fit available preview image size: " +
                    mCameraHeight + " x " + mCameraWidth + " (" + mCameraHeight * mCameraWidth + ")");
    }

//	set preview size, and make any resize, rotate or reformatting changes here

    public void setCameraInfo (Camera mCamera, int nCam)
    {
        String TAG = "setCameraInfo";
        Camera.Parameters params = mCamera.getParameters();
        selectPreviewSize(mCamera, nCam);
        if (mCameraWidth > 0 && mCameraHeight > 0)
            params.setPreviewSize(mCameraWidth, mCameraHeight);
        else Log.e(TAG, "Unknown preview size?");
        if (adjustFrameRateFlag)
            params.setPreviewFrameRate(mCameraFrameRate);	// deprecated  ?
        if (adjustExposureCompensationFlag)
            params.setExposureCompensation(minExposureCompensation);
        exposureCompensation = params.getExposureCompensation();
        if (DBG) Log.i(TAG, "exposureCompensation " + exposureCompensation);
        // check whether ISO adjustement supported
        String supportedValues = params.get("iso-values");
        if (supportedValues != null) {
            if (DBG) Log.v(TAG, "ISO " + supportedValues);
            if (adjustISOFlag) { // e.g. "100", "200", "400", "800", "1600", "auto", "ISO_HJR", ...
                params.set("iso", Integer.toString(ISO));
                String iso = params.get("iso");
                if (DBG) Log.i(TAG, "ISO "+iso);
            }
        }
        else Log.w(TAG, "ISO adjustment not supported");
        if (setFocusModeFlag) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
//			params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        try {	// try to set new params in camera (may cause exception)
            mCamera.setParameters(params);
        } catch (Exception e) {
            Log.e(TAG, "Camera parameter setting failed "+ e.getMessage() + " " + e);
        }
    }

    // show detailed information for specific camera in log use ExtraInfo.showCameraInfo()
    // show information for all installed cameras use ExtraInfo.showCameraInfoAll()

    // return human readable string from numeric code for picture format
    public String pictureFormatString (int iform)
    {
        switch(iform) {
            case ImageFormat.JPEG: return "JPEG";
            case ImageFormat.NV16: return "NV16";
            case ImageFormat.NV21: return "NV21";	// default for Camera preview ?
            case ImageFormat.RGB_565: return "RGB_565";
            case ImageFormat.YUY2: return "YUY2";
            case ImageFormat.YV12: return "YV12";
            case ImageFormat.YUV_420_888: return "YUV_420_888";	// preferred for Camera2 preview
            case ImageFormat.RAW10: return "RAW10"; 	// requires API 21 ?
            case ImageFormat.RAW_SENSOR: return "RAW_SENSOR";  // requires API 21 ?
            case ImageFormat.UNKNOWN:
            default: return "UNKNOWN";
        }
    }

//////////////////////////////////////////////////////////////////////////////////////////////////

    // For Android 6.0 (API Level 25)  permission requests

    private static final int REQ_PERMISSION_THISAPP = 0; // unique code for permissions request
    private static boolean bUseCameraFlag = true;			   // we want to use the camera
    private static boolean bCameraPermissionGranted = false;   // have CAMERA permission

    private void getPermissions ()
    {
        String TAG = "getPermissions";
        if (DBG) Log.v(TAG, "in getPermissions()");
        if (Build.VERSION.SDK_INT >= 23) {            // need to ask at runtime as of Android 6.0
            String sPermissions[] = new String[2];    // space for possible permission strings
            int nPermissions = 0;	// count of permissions to be asked for
            if (bUseCameraFlag) {    // protection level: dangerous
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    bCameraPermissionGranted = true;
                else sPermissions[nPermissions++] = Manifest.permission.CAMERA;
            }
            if (nPermissions > 0) {
                if (DBG) Log.d(TAG, "Need to ask for " + nPermissions + " permissions");
                if (nPermissions < sPermissions.length)
                    sPermissions = Arrays.copyOf(sPermissions, nPermissions);
                if (DBG) {
                    for (String sPermission : sPermissions) Log.w(TAG, sPermission);
                }
                requestPermissions(sPermissions, REQ_PERMISSION_THISAPP);    // start the process
            }
        } else {    // in earlier API, permission is dealt with at install time, not run time
            if (bUseCameraFlag) bCameraPermissionGranted = true;
        }
    }

    //	Note: onRequestPermissionsResult happens *after* user has interacted with the permissions request
    //  So, annoyingly, may have to now (re-)do things that didn't happen in onCreate() /  onResume()
    //  because permissions were not given yet.

    @Override
    // overrides method in android.app.Activity
    public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        String TAG = "onRequestPermitResult";
        if (requestCode != REQ_PERMISSION_THISAPP) {    // check that this is a response to our request
            Log.e(TAG, "Unexpected requestCode " + requestCode);    // should not happen ?
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        int n = grantResults.length;
        if (DBG) Log.w(TAG, "requestCode=" + requestCode + " for " + n + " permissions");
        for (int i = 0; i < n; i++) {
            if (DBG) Log.w(TAG, "permission " + permissions[i] + " " + grantResults[i]);
            switch (permissions[i]) {
                case Manifest.permission.CAMERA:
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        if (DBG) Log.w(TAG, "CAMERA Permission granted (" + i + ")");
                        bCameraPermissionGranted = true;
                        // redo the setup in onResume(...) ?
                    } else {
                        bUseCameraFlag = false;
                        String str = "You must grant CAMERA permission to use the camera!";
                        Log.e(TAG, str);
                        makeToast(str, 1);
                    }
                    break;
            }
        }
    }

    private void makeToast (CharSequence message, int nLength)
    {
        // Toast.LENGTH_SHORT == 0, Toast.LENGTH_LONG == 1
        Toast toast = Toast.makeText(this, message, nLength);
        toast.show();
    }



// CLASS ----------------------------------------------------------------------

    class DrawOnTop extends View
    {
        String TAG="DrawOnTop";
        Paint mPaintBlack, mPaintYellow, mPaintRed, mPaintGreen, mPaintBlue;
        RectF barRect = new RectF();	// rectangular bar (used for A, B, C bars)
        Rect src = new Rect();	// rectangles for copying bitmap onto screen
        Rect dst = new Rect();	// actual position and shape of these defined later

        double mFOE_x, mFOE_y, mTimeToContact;

        int mImageHeight = mCameraHeight;
        int mImageWidth = mCameraWidth;

        boolean drawBarsFlag=true;	// show A, B, C as bars
        boolean drawTextFlag=true;	// show A, B, C, TTC, FOE etc. as text
        boolean drawFOEFlag=true;	// attempt to show estimated FOE

        boolean superimposeFlag=false;		// Take image data and display on screen (unscaled)
        boolean subsampledShowFlag=true;	// show subsampled image on screen
        boolean gradientShowFlag=true;		// show gradient images etc on screen

        final int mTextsize = 40;				// superimposed text size
        final int leading = mTextsize * 6 / 5;	// line spacing

//		We can afford to create new objects here since this is not in inner loop

        public DrawOnTop (Context context) {		// Creator for class
            super(context);
            String TAG="DrawOnTop";

            if (DBG) Log.d(TAG, "in creator");

            mPaintBlack = makePaint(Color.BLACK);
            mPaintYellow = makePaint(Color.YELLOW);
            mPaintRed = makePaint(Color.RED);
            mPaintGreen = makePaint(Color.GREEN);
            mPaintBlue = makePaint(Color.BLUE);

            mBitmap = null;
            mBitmapE = mBitmapEx = mBitmapEy = mBitmapEt = mBitmapG = mBitmapGEt = null;
            mRawYUVData = null;
            mRawRGBData = null;
//			mRawGrayData = null;

            mBlockData = mBlockData_Prev = null;

            mBlockDisplayE = mBlockDisplayEx = mBlockDisplayEy = mBlockDisplayEt = null;
            mBlockDisplayG = mBlockDisplayGEt = null;

            AA = new double[3][3];
            Mtranspose = new double[3][3];
            bb = new double[3];
            xx = new double[3];
            //multiscale
//            xx_scales = new double[3];
            xx_scales = new double[scales][3];
            xx_tmp = new double[3];
            zz = new double[3];
            //multiscale
            zz_scales = new double[scales][3];
            crs = new double[3];

            xx[0] = xx[1] = xx[2] = 0;	// before a calculated xx[] is available
            zz[0] = zz[1] = zz[2] = 0;

            mTimeToContact = 0;
            mFOE_x = mFOE_y = 0;

            //	stack for A, B, C (and dT)
            if (mStack > 0) {
                aStack = new double[mStack];
                bStack = new double[mStack];
                cStack = new double[mStack];
                iTStack = new int[mStack];
                for (int i = 0; i < mStack; i++) aStack[i]=0;
                for (int i = 0; i < mStack; i++) bStack[i]=0;
                for (int i = 0; i < mStack; i++) cStack[i]=0;
                for (int i = 0; i < mStack; i++) iTStack[i]=0;

                aStackScales = new double[scales][mStack];
                bStackScales = new double[scales][mStack];
                cStackScales = new double[scales][mStack];
            }

        }

        Paint makePaint (int color) {
            Paint mPaint = new Paint();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(color);
            mPaint.setTextSize(mTextsize);
            mPaint.setTypeface(Typeface.MONOSPACE);
            return mPaint;
        }


//		Here is what we do when we are asked to draw on the canvas:
//		first, the TTC calculation
//		and optionally the creation of overlay bitmaps to show gradients.
//		If we do multi-scale later, we will have to separate the calculations
//		from the subimage output on-screen (see gradientShowFlag)

        @Override
        protected void onDraw (Canvas canvas)
        {
            String TAG="onDraw";
            int canvasHeight = canvas.getHeight();
            int canvasWidth = canvas.getWidth();
            double deter;
            boolean valid = true;

            if (mBitmap == null) {	// sanity check
                super.onDraw(canvas);
                Log.e(TAG, "No bitmap");
                return;
            }

            if (verboseFlag) Log.i(TAG, "Canvas " + canvasHeight + " x " + canvasWidth);
            //	NOTE: is the following the time between frames, or,
            //	the time between when we get them from the buffer ?
            idT = (int) (mCurtime - mPretime);	// time difference in msec
            if (verboseFlag) Log.d(TAG, "dT " + idT + " msec");
            if (idT == 0) {
                Log.e(TAG, "Zero time interval?");
                valid = false;	// results should be ignored ?
                idT = 1;		// should not happen - avoid division by zero
            }

            // size of the image block we will actually process
            int nPixels;	// total pixels used in block averaged image

            mBlockHeight = mImageHeight / mBlock;
            mBlockWidth = mImageWidth / mBlock;
            nPixels = (mBlockHeight-1) * (mBlockWidth-1);	// NOTE: margin of one because of first differences

            if (verboseFlag)
                Log.i(TAG, "Image " + mImageHeight + " x " + mImageWidth + " Block " + mBlockHeight + " x " + mBlockWidth);

            //	Superimpose preview image on screen --- this is instructive in that it shows the size of
            //	the preview relative to the screen size --- and also shows the lag between the direct
            //	preview and what it is superimposed on.  Default is not to do this.
            if (superimposeFlag) showPreviewOnScreen(canvas);

            // We use only luminance (Y) --- ignore chrominance (U, V), which is stored at end.
            // NOTE: byte data type is signed, so need to AND with 0xFF
            // First, simple case, just copy over image (from byte[] to int[])
            if (mBlock == 1) {
                for (int kk=0; kk < mImageHeight * mImageWidth; kk++)
                    mBlockData[kk] = mRawYUVData[kk] & 0xFF;
            }
            // Next, just extract a central patch of the image...(check this)
            else if (centerExtractFlag) {	// extract center of preview image
                int inx = (mImageHeight - mBlockHeight) / 2 * mImageWidth  + (mImageWidth - mBlockWidth) / 2;
                int onx = 0;	// 'out' pointer
//                Log.i(TAG, "mBlockData.length " + mBlockData.length);
//                Log.i(TAG, "mBlockHeight " + mBlockHeight);
//                Log.wtf(TAG, "Image H " + mImageHeight);
//                Log.i(TAG, "mBlockWidth " + mBlockWidth);
//                Log.i(TAG, "mBlockWidth*H " + mBlockHeight*mBlockWidth);
//                Log.i(TAG, "mRawYUVData.length " + mRawYUVData.length);
                for (int ii = 0; ii < mBlockHeight; ii++) {
                    for (int jj = 0; jj < mBlockWidth; jj++) {

//                        Log.i(TAG, "onx " + onx);
//                        Log.i(TAG, "inx " + onx);
                        mBlockData[onx++] = (mRawYUVData[inx++] & 0xFF) * mBlock;
                    }
                    inx += mImageWidth - mBlockWidth;	// update 'in' pointer
                }
//                Log.i(TAG, "onx " + onx);
//                Log.i(TAG, "inx " + onx);
            }
            else{	// block average: from mRawYUVData[] to mBlockData[]
                // assume mImageWidth and mImageHeight are divisible by mBlock
                // Extract luminance and block average (from byte[] to int[])

                // TODO: your block averaging code here...

                //signle block average -- save for display
                //from TTC embelish
                int anx, bnx, cnx, inx, onx=0;
                anx = 0;//block index vertical direction
                for (int ii = 0; ii < mBlockHeight; ii++) {//make blocks in the height
                    bnx = anx;//index of the block
                    for (int jj = 0; jj < mBlockWidth; jj++) {//make blocks in the horizontal direction
                        int sum = 0;
                        cnx = bnx;//index of the block in the row

                        // block average
                        for (int kk = 0; kk < mBlock; kk++) {
                            inx = cnx;
                            for (int ll = 0; ll < mBlock; ll++) {
                                sum += mRawYUVData[inx++] & 0xFF;//process the data
                            }
                            cnx += mImageWidth;// go to next row
                        }
                        mBlockData[onx++] = sum / mBlock;//compute and save average
                        bnx += mBlock;//go to next block (horizontal)
                    }
                    anx += mImageWidth * mBlock;//go to next row of blocks
                }

                if(multiScaleFlag)
                {
                    //TODO: consider just reaveraging the blocks instead computing from source



                    for(int s = 0; s < scales; s++)
                    {
                        mBhScales[s] = mImageHeight / mBlockScales[s];
                        mBwScales[s] = mImageWidth / mBlockScales[s];

//                        int anx, bnx, cnx, inx, onx=0;
                        onx = 0;
                        anx = 0;//block index vertical direction
                        for (int ii = 0; ii < mBhScales[s]; ii++) {//make blocks in the height
                            bnx = anx;//index of the block
                            for (int jj = 0; jj < mBwScales[s]; jj++) {//make blocks in the horizontal direction
//                                int sum = 0;//TODO consider changing to float to avoid overflow
                                double sum = 0;//TODO consider changing to float to avoid overflow
                                cnx = bnx;//index of the block in the row

                                // block average
                                for (int kk = 0; kk < mBlockScales[s]; kk++) {
                                    inx = cnx;
                                    for (int ll = 0; ll < mBlockScales[s]; ll++) {
//                                        sum += mRawYUVData[inx++] & 0xFF;//process the data
                                        sum += mRawYUVData[inx++] & 0xFF;// <-- change to FFFF? I think will map to a larger range
                                    }
                                    cnx += mImageWidth;// go to next row
                                }
                                mBlockDataScales[s][onx++] = (int)sum / mBlockScales[s];//compute and save average
                                bnx += mBlockScales[s];//go to next block (horizontal)
                            }
                            anx += mImageWidth * mBlockScales[s];//go to next row of blocks
                        }
                    }


                }

            }
            // ...



            if (subsampledShowFlag) 	// show subsampled grey image in top left corner
                showSubSampled (canvas);

            if (correctDriftFlag) 	// compensate for overall brightness drift between frames
                adjustBrightness(mBlockData, mBlockData_Prev, mBlockHeight, mBlockWidth);

            if (histogramFlag) 	// histogram as debugging/sanity check
                showHistogram(mBlockData, mBlockHeight, mBlockWidth);

            deter = TTCcalculation(canvas); //so we get some display in corner
            if(multiScaleFlag)
                deter = TTCcalculationMS(canvas);


            if (deter != 0) {
//				Log.w(TAG, "a " + xx[0] + " b " + xx[1] + " c " + xx[2] + " valid " + valid + " deter " + deter);

                if (deter < 1.0) {	// determinant here is huge due to scaling
                    if (verboseFlag) Log.e(TAG, "Determinant " + deter);
                    valid = false;	// zero (or small) determinant
                }

                if (mStack > 0) { 	// use sliding window average if we stacked up past values
                    slidingWindow();
                }
                else {	// no stack of saved values -- use current values
                    zz[0] = xx[0]; zz[1] = xx[1]; zz[2] = xx[2];
                    //zz is the average
                    zdT = idT;

                    //minimum TTC is the best
                    if(multiScaleFlag) {
                        double minTTC = Double.MAX_VALUE;

                        scaleChoice = -1; //just in case

                        for (int s = 0; s < scales; s++) {
                            if(abs(-1/xx_scales[s][2]) < minTTC)
                            {
                                minTTC = abs(-1/xx_scales[s][2]);
                                scaleChoice = s;
                            }
                        }

                        zz = xx_scales[scaleChoice].clone();

//                        Log.wtf(TAG, "scaleChoice: " + scaleChoice);
                    }

                }

//				Log.w(TAG, "a " + zz[0] + " b " + zz[1] + " c " + zz[2] + " valid " + valid);

                if (zz[2] == 0) valid = false;	// avoid division by zero

                if (drawBarsFlag) drawTheBars (canvas);	// A, B, and C

                if (valid) {
                    mTimeToContact = -1.0/zz[2];	// time to contact (in frames)
                    mFOE_x = zz[0]/zz[2];		// FOE x in pixels from center in subsampled preview
                    mFOE_y = zz[1]/zz[2]; 		// FOE y in pixels from center in subsampled preview
                    if (verboseFlag) {
                        Log.i(TAG, "ABC " + zz[0] + " " + zz[1] + " " + zz[2]);
                        Log.i(TAG, "TTC " + mTimeToContact + " frames ");
                        Log.i(TAG, "FOE " + mFOE_x + " " + mFOE_y + " pixels");
                    }
                    if (drawFOEFlag) drawTheFOE(canvas);
                }
                else {
                    mTimeToContact = 0;
                    mFOE_x = mFOE_y = 0;
                }
                if (drawTextFlag) drawTheText(canvas, Etlowcount, nPixels);
            }	// if end of if deter != 0


            super.onDraw(canvas);

        } // end onDraw method

//		Build up totals of derivative products over the image, and then solve for (A, B, C)
//		Downsampled, block averaged current image is in mBlockData
//		Downsampled, block averaged previous image is in mBlockData_Prev

//		Use AA[][] for coefficient matrix
//		Use bb[] for right hand side of equations
//		Use xx[] for solution (A,B,C)
//		Return determinant (zero if singular)

//		Can use Mtranspose[][] for intermediate matrix if needed
//		Can use crs[] for intermediate vector if needed

//		NOTE: against better programming practices, we use global variables
//		because we don't want the cost of memory allocation every time this is run.

        double TTCcalculation (Canvas canvas)
        {			// accumulate totals for TTC calculation
            int nPixels;	// how many places where we compute derivatives
            double deter;

            double Ex2_integral=0,  ExEy_integral=0,  Ey2_integral=0;
            double GEx_integral=0,  GEy_integral=0,   G2_integral=0;
            double ExEt_integral=0, EyEt_integral=0,  GEt_integral=0;
            boolean valid = true;	// whether we have valid data

            // the actual computation of the components of translational motion

            // size of the image block we will actually process
            mBlockHeight = mImageHeight / mBlock;
            mBlockWidth = mImageWidth / mBlock;
            nPixels = (mBlockHeight-1) * (mBlockWidth-1);	// NOTE: margin of one because of first differences

            int Ex, Ey, Et;
            double x, y, G;
            final int Et_threshold = 4*mBlock*mBlock;	// TODO: somewhat arbitrary?


//			TODO: your TTC code here
//            int curr_px;
            int c11,c12,c21,c22;//for pixel extraction //current frame
            int p11,p12,p21,p22;//previous frame
//double dt;
//            int dt;
            int inx0;

            int onx = 0;	// 'out' pointer
            int i,j;

//            dt = (int)(mCurtime - mPretime);
//            Log.i(TAG, "dt " + dt);

            Etlowcount = 0;//reset the count

            int A, B, C, D;

            for (int ii = 0; ii < mBlockHeight-1; ii++) {
                //more efficient copying
                onx = ii * mBlockWidth;//current

                c12 = mBlockData[onx + 1]; //right one
                c22 = mBlockData[onx + mBlockWidth + 1];//down 1, right 1
                p12 = mBlockData_Prev[onx + 1]; //right one
                p22 = mBlockData_Prev[onx + mBlockWidth + 1];//down 1, right 1

                for (int jj = 0; jj < mBlockWidth-1; jj++) {
//            for (int ii = 0; ii < mBlockHeight - 1; ii++) {
//                for (int jj = 0; jj < mBlockWidth - 1; jj++) {


                    onx = ii * mBlockWidth + jj;//current
//                    curr_px = mBlockData[onx];

                    //TODO: consider reusing values
                    //current frame
                    c11 = c12; //curr px
                    c12 = mBlockData[onx + 1]; //right one
                    c21 = c22;//down 1
                    c22 = mBlockData[onx + mBlockWidth +1];//down 1, right 1

                    //previous
                    p11 = p12; //curr px
                    p12 = mBlockData_Prev[onx]; //right one
                    p21 = p22;//down 1
                    p22 = mBlockData_Prev[onx + mBlockWidth + 1];//down 1, right 1

                    A = c22 - p11;
                    B = c11 - p22;
                    C = c21 - p12;
                    D = c12 - p21;
//                    c11 = mBlockData[onx]; //curr px
//                    c12 = mBlockData[onx + 1]; //right one
//                    c21 = mBlockData[onx + mBlockWidth];//down 1
//                    c22 = mBlockData[onx + mBlockWidth + 1];//down 1, right 1
//
//                    //previous
//                    p11 = mBlockData_Prev[onx]; //curr px
//                    p12 = mBlockData_Prev[onx + 1]; //right one
//                    p21 = mBlockData_Prev[onx + mBlockWidth];//down 1
//                    p22 = mBlockData_Prev[onx + mBlockWidth + 1];//down 1, right 1

//                    Et = ((c11 + c12 + c21 + c22) - (p11 + p12 + p21 + p22)) / 4; //time division is at the end
                    Et = (A+B+C+D) / 4; //time division is at the end

//                    Et = (curr_px - mBlockData_Prev[onx]);//division is at the end
//                    if(zdT != 0)//avoid divide by 0
////                        Et = (curr_px - mBlockData_Prev[onx]) / dt;//seems odd that it's an int
//                        Et = (curr_px - mBlockData_Prev[onx]) / zdT;//seems odd that it's an int
//                    else
//                        Et=0;

                    if (abs(Et) < Et_threshold)//include Math package
                    {
                        Etlowcount++;
                        continue;
                    }

//                    Ex = ((c12 - c11) + (c22 - c21) + (p12 - p11) + (p22 - p21)) / 4;
                    Ex = (A - B - C + D) / 4;
//                    Ey = ((c21 - c11) + (c22 - c12) + (p21 - p11) + (p22 - p12)) / 4;
                    Ey =  (A - B + C - D) /4 ;

//                    Ex = curr_px - mBlockData[onx + 1];//one to the right
//                    Ey = curr_px - mBlockData[onx + mBlockWidth];//one down

                    //compute x & y
                    x = jj - mBlockWidth / 2;
                    y = ii - mBlockHeight / 2;

                    G = x * Ex + y * Ey;

                    inx0 = onx;
                    if (gradientShowFlag) buildGradientBitmaps(Ex, Ey, Et, G, inx0);

                    Ex2_integral = Ex2_integral + Ex * Ex;
                    ExEy_integral = ExEy_integral + Ex * Ey;
                    Ey2_integral = Ey2_integral + Ey * Ey;
                    GEx_integral = GEx_integral + G * Ex;
                    GEy_integral = GEy_integral + G * Ey;
                    G2_integral = G2_integral + G * G;
                    ExEt_integral = ExEt_integral + Ex * Et;
                    EyEt_integral = EyEt_integral + Ey * Et;
                    GEt_integral = GEt_integral + G * Et;
                }
            }

            //Draw bitmaps in the top left corner of the screen
            if (gradientShowFlag) showGradients(canvas);
//			...


//			NOTE: ignore the calculations the first time around, the results are not useful
//			since the saved/remembered image is all zero, and the old time is zero.

            if (mCount > 1) { // Calculate TimeToContact from accumulated sums
                AA[0][0] = Ex2_integral;
                AA[0][1] = ExEy_integral;
                AA[0][2] = -GEx_integral;
                AA[1][0] = ExEy_integral;
                AA[1][1] = Ey2_integral;
                AA[1][2] = -GEy_integral;
                AA[2][0] = -GEx_integral;
                AA[2][1] = -GEy_integral;
                AA[2][2] = G2_integral;

                bb[0] = -ExEt_integral;
                bb[1] = -EyEt_integral;
                bb[2] = +GEt_integral;

                deter = Solve33.solveEquations(AA, Mtranspose, bb, xx, crs);	// solve 3 linear equations
                // leaves result in xx
                return deter;
            }
            else return 0;	// no valid data yet
        }

        //TODO: multiscale TTC calc
        double TTCcalculationMS (Canvas canvas)
        {
            if (mCount < 1) { //there is no data
                return 0;
            }

// accumulate totals for TTC calculation
//            int nPixels;	// how many places where we compute derivatives
            double deter;
            double minDeter = Double.MAX_VALUE;

//            double minTTC = Double.MAX_VALUE;

            double Ex2_integral=0,  ExEy_integral=0,  Ey2_integral=0;
            double GEx_integral=0,  GEy_integral=0,   G2_integral=0;
            double ExEt_integral=0, EyEt_integral=0,  GEt_integral=0;
            boolean valid = true;	// whether we have valid data

            // the actual computation of the components of translational motion

            // size of the image block we will actually process
            mBlockHeight = mImageHeight / mBlock;
            mBlockWidth = mImageWidth / mBlock;
//            nPixels = (mBlockHeight-1) * (mBlockWidth-1);	// NOTE: margin of one because of first differences

            int Ex, Ey, Et;
            double x, y, G;
//            final int Et_threshold = 4*mBlock*mBlock;	// TODO: somewhat arbitrary?
            int Et_threshold;

            int c11,c12,c21,c22;//for pixel extraction //current frame
            int p11,p12,p21,p22;//previous frame


            int onx;	// 'out' pointer

            //not the A,B,C solutions
            int A, B, C, D;

            for(int s = 0; s < scales; s++) {
                Et_threshold = 4 * mBlockScales[s] * mBlockScales[s];
//            Etlowcount = 0;//reset the count

                mBhScales[s] = mImageHeight / mBlockScales[s];
                mBwScales[s] = mImageWidth / mBlockScales[s];

//                Log.wtf(TAG, s + " scale " + mBlockScales[s]);
//                Log.wtf(TAG, "length of array @ scale: " + mBlockDataScales[s].length);
//                Log.wtf(TAG, "mBh[s]: " + mBhScales[s]);
//                Log.wtf(TAG, "mBw[s]: " + mBwScales[s]);
//                Log.wtf(TAG, "w x h: " + mBhScales[s] * mBwScales[s]);

                for (int ii = 0; ii < mBhScales[s] - 1; ii++) {
                    //more efficient copying
                    onx = ii * mBwScales[s];//current

                    c12 = mBlockDataScales[s][onx + 1]; //right one
                    c22 = mBlockDataScales[s][onx + mBwScales[s] + 1];//down 1, right 1
                    p12 = mBlockDataScales_Prev[s][onx + 1]; //right one
                    p22 = mBlockDataScales_Prev[s][onx + mBwScales[s] + 1];//down 1, right 1
//                        Log.i(TAG, "ii: " + ii);
                    for (int jj = 0; jj < mBwScales[s] - 1; jj++) {
                        onx = ii * mBwScales[s] + jj;//current

                        //current frame
                        c11 = c12; //curr px
                        c12 = mBlockDataScales[s][onx + 1]; //right one
                        c21 = c22;//down 1
                        if(onx + mBwScales[s] + 1 > mBlockDataScales[s].length)
                            Log.i(TAG, "ii,jj: " + ii + "," + jj);
                        c22 = mBlockDataScales[s][onx + mBwScales[s] + 1];//down 1, right 1

                        //previous
                        p11 = p12; //curr px
                        p12 = mBlockDataScales_Prev[s][onx]; //right one
                        p21 = p22;//down 1
                        p22 = mBlockDataScales_Prev[s][onx + mBwScales[s] + 1];//down 1, right 1

                        A = c22 - p11;
                        B = c11 - p22;
                        C = c21 - p12;
                        D = c12 - p21;
                        Et = (A + B + C + D) / 4; //time division is at the end

                        if (abs(Et) < Et_threshold)//include Math package
                        {
//                        Etlowcount++;
                            continue;
                        }

                        Ex = (A - B - C + D) / 4;
                        Ey = (A - B + C - D) / 4;

                        //compute x & y
                        x = jj - mBwScales[s] / 2;
                        y = ii - mBhScales[s] / 2;

                        G = x * Ex + y * Ey;

//                    if (gradientShowFlag) buildGradientBitmaps(Ex, Ey, Et, G, inx0);

                        Ex2_integral = Ex2_integral + Ex * Ex;
                        ExEy_integral = ExEy_integral + Ex * Ey;
                        Ey2_integral = Ey2_integral + Ey * Ey;
                        GEx_integral = GEx_integral + G * Ex;
                        GEy_integral = GEy_integral + G * Ey;
                        G2_integral = G2_integral + G * G;
                        ExEt_integral = ExEt_integral + Ex * Et;
                        EyEt_integral = EyEt_integral + Ey * Et;
                        GEt_integral = GEt_integral + G * Et;
                    }


                    //Draw bitmaps in the top left corner of the screen
//            if (gradientShowFlag) showGradients(canvas);
//			...


                    AA[0][0] = Ex2_integral;
                    AA[0][1] = ExEy_integral;
                    AA[0][2] = -GEx_integral;
                    AA[1][0] = ExEy_integral;
                    AA[1][1] = Ey2_integral;
                    AA[1][2] = -GEy_integral;
                    AA[2][0] = -GEx_integral;
                    AA[2][1] = -GEy_integral;
                    AA[2][2] = G2_integral;

                    bb[0] = -ExEt_integral;
                    bb[1] = -EyEt_integral;
                    bb[2] = +GEt_integral;

//                    deter = Solve33.solveEquations(AA, Mtranspose, bb, xx, crs);    // solve 3 linear equations
//                    deter = Solve33.solveEquations(AA, Mtranspose, bb, xx_scales, crs);    // solve 3 linear equations
                    // leaves result in xx
//                    if (abs(1 / xx[2]) < minTTC)//this scale is the best -- take abs to handle negative ttc
//                    {
//                        minTTC = abs(1 / xx[2]);
//                        xx = xx_scales.clone();// or do I need to do array copy?
//                        System.arraycopy(xx_scales,0,xx,0,xx.length);
//                        minDeter = deter;
//                        scaleChoice = s;
//                    }

                    deter = Solve33.solveEquations(AA, Mtranspose, bb, xx_tmp, crs);    // solve 3 linear equations
                    xx_scales[s] = xx_tmp.clone();//save result from all the scales
                    if (deter < minDeter)
                    {
                        minDeter = deter;
                    }

                }
            }

            return minDeter; //TODO: set this
        }

        void drawTheBars (Canvas canvas)
        {
            int canvasHeight = canvas.getHeight();
            int canvasWidth = canvas.getWidth();
            int barWidth = canvasWidth/50;
            for (int ii = 0; ii < 3; ii++) { 	// draw A, B, C bars
                float size;
                barRect.bottom = barRect.top = canvasHeight/2;	// zero is in middle
                barRect.left   = canvasWidth + (canvasWidth/20) * (ii - 4);
                barRect.right  = barRect.left + barWidth;
                // scales for A, B, C differ since A = f(U/Z), B = f(V/Z) while C = W/Z
                if (ii == 2) size = (float) (zz[ii] * 1000000 / zdT);	// scale for C
                else size = (float) (zz[ii] * 1000 / zdT);	// scale for A and B
                if (size >= 0) {	// positive (green and up)
                    barRect.top = barRect.top - size;
                    canvas.drawRect(barRect, mPaintGreen);
                }
                else {				// negative (red and down)
                    barRect.bottom = barRect.bottom - size;
                    canvas.drawRect(barRect, mPaintRed);
                }
            }
        }

        void drawTheFOE (Canvas canvas)
        { 			// attempt at drawing FOE
            String imageFOEPnt;
            imageFOEPnt = "o";
            int FOEpX = screenWidth/2 + (int) mFOE_x;	// scaled and positioned
            int FOEpY = screenHeight/2 + (int) mFOE_y;	// scaled and positioned
//			TODO: get correct FOE position on top of camera preview!
//			mFOE_x = mFOE_x * screenWidth / mBlockWidth;	// scale to fit?
//			mFOE_y = mFOE_y * screenHeight / mBlockHeight;	// scale to fit?
            drawTextOnBlack(canvas, imageFOEPnt, FOEpX, FOEpY, mPaintRed);
        }

        void drawTheText (Canvas canvas, int Etlowcount, int nPixels)
        {
//			int canvasHeight = canvas.getHeight();
            int canvasWidth = canvas.getWidth();
            int marginWidth = canvasWidth * 5 / 10;	// how far right to put text
            int lowPercent = Etlowcount * 100 / nPixels;	// percentage with Et below threshold
            String imageTTCStr, imageABCStr, imageFOEStr, imageDTStr;
            double mTimeToContactSec = mTimeToContact * zdT / 1000;	// convert to sec
            if (verboseFlag) Log.i(TAG, "TTC " + mTimeToContactSec + " sec ");
            imageABCStr = "ABC " + String.format("%9.4f", zz[0]) + String.format("%9.4f", zz[1]) + String.format("%9.4f", zz[2]);
            drawTextOnBlack(canvas, imageABCStr, marginWidth+10, 90, mPaintYellow);
            imageTTCStr = "TTC " + String.format("%9.0f", mTimeToContact) + " (frames)" + String.format("%7.0f",  mTimeToContactSec) + " (sec)";
            drawTextOnBlack(canvas, imageTTCStr, marginWidth+10, 90+leading, mPaintYellow);
            imageFOEStr = "FOE ( " + String.format("%6.0f", mFOE_x) + ", " + String.format("%6.0f", mFOE_y) + ") (pixel)";
            drawTextOnBlack(canvas, imageFOEStr, marginWidth+10, 90+leading*2, mPaintYellow);
            imageDTStr = "dT " + String.format("%3d",  idT) + " msec " + String.format("%3d", lowPercent) + "% Et low";
            drawTextOnBlack(canvas, imageDTStr, marginWidth+10, 90+leading*3, mPaintYellow);

            //multiscale
            if(multiScaleFlag) {
                String scaleStr, scaleTTCStr, tmpscaleStr;
                scaleStr = "scale(" + String.format("%d", scaleChoice) + ") = " + String.format("%d", mBlockScales[scaleChoice]);
                drawTextOnBlack(canvas, scaleStr, marginWidth+10, 90+leading*4, mPaintYellow);
                for(int s = 0; s < scales; s++)
                {
                    tmpscaleStr = "scale(" + String.format("%d", s) + ") = " + String.format("%d", mBlockScales[s]);
                    scaleTTCStr = tmpscaleStr + ": TTC " + String.format("%f", -1/zz_scales[s][2]);// + " (frames)" + String.format("%f",  mTimeToContactSec) + " (sec)";
                    drawTextOnBlack(canvas, scaleTTCStr, marginWidth+10, 90+leading*(5+s), mPaintYellow);
                }
            }
        }

        void showPreviewOnScreen (Canvas canvas)
        {
            int canvasHeight = canvas.getHeight();
            int canvasWidth = canvas.getWidth();
            int marginWidth=0;

//			Need to convert from YUV to RGB for this
//			YUV image is in mRawYUVData mImageHeight x mImageWidth
            decodeYUV420SP(mRawRGBData, mRawYUVData, mImageHeight, mImageWidth);
            // Setup bitmap
            mBitmap.setPixels(mRawRGBData, 0, mImageWidth, 0, 0, mImageWidth, mImageHeight);
            // set up rectangles for bitmap drawing below
            src.left = 0;
            src.top = 0;
            src.right = mImageWidth;
            src.bottom = mImageHeight;
            dst.left = marginWidth;
            dst.top = 0;
            dst.right = canvasWidth-marginWidth;
            dst.bottom = canvasHeight;
            if (verboseFlag) Log.i(TAG, "Image bitmap " + src + " -> " + dst);
            if (stretchToScreenFlag)
                canvas.drawBitmap(mBitmap, src, dst, mPaintBlack); // stretch to fit screen
            else canvas.drawBitmap(mBitmap, src, src, mPaintBlack); // draw actual size (aligned top left corner)

            if (marginWidth > 0) { 					// Draw black borders if needed
                canvas.drawRect(0, 0, marginWidth, canvasHeight, mPaintBlack);
                canvas.drawRect(canvasWidth - marginWidth, 0, canvasWidth, canvasHeight, mPaintBlack);
            }

        }

        void showSubSampled (Canvas canvas)
        {
            int grey, inx=0;

            for (int ii=0; ii < mBlockHeight; ii++) {	// straight grey level
                for (int jj=0; jj < mBlockWidth; jj++) {
                    grey = mBlockData[inx] / mBlock;	// reduce to range 0 - 255
                    mBlockDisplayE[inx] = transparency | grey << 16 | grey << 8 | grey;
                    inx++;
                }
            }
            // Setup bitmap
            mBitmapE.setPixels(mBlockDisplayE, 0, mBlockWidth, 0, 0, mBlockWidth, mBlockHeight);
            canvas.drawBitmap(mBitmapE, 0, 0, mPaintBlack);
        }

        //		Build the bitmaps for showing the gradients
        //		This could be speeded up by using two tables of RGB values
        void buildGradientBitmaps (int Ex, int Ey, int Et, double G, int inx0)
        {
            final int gain_xy=4;	// gain for E_x and E_Y pictures
            final int gain_t=1;		// gain for E_t pictures
            int red, green, blue=0, grey;

            red =  green = 0;
            grey = Ex * gain_xy / mBlock;	// draw Ex
            if (grey < -255) red = 255;
            else if (grey < 0) red = -grey;
            else if (grey < 255) green = grey;
            else green = 255;
            mBlockDisplayEx[inx0] = transparency | red << 16 | green << 8 | blue;

            red =  green = 0;
            grey = - Ey * gain_xy / mBlock;	// flip sign - y increases downward
            if (grey < -255) red = 255;
            else if (grey < 0) red = -grey;
            else if (grey < 255) green = grey;
            else green = 255;
            mBlockDisplayEy[inx0] = transparency | red << 16 | green << 8 | blue;

            red =  green = 0;
            grey = Et * gain_t / mBlock;	// use different gain for E_t
            if (grey < -255) red = 255;
            else if (grey < 0) red = -grey;
            else if (grey < 255) green = grey;
            else green = 255;
            mBlockDisplayEt[inx0] = transparency | red << 16 | green << 8 | blue;

            red =  green = 0;
            grey = (int) (G / (mBlockWidth));	// draw G
            if (grey < -255) red = 255;
            else if (grey < 0) red = -grey;
            else if (grey < 255) green = grey;
            else green = 255;
            mBlockDisplayG[inx0] = transparency | red << 16 | green << 8 | blue;

            red =  green = 0;
            grey = (int) (G * Et / (mBlockWidth*mBlock*mBlock));	// draw G * Et
            if (grey < -255) red = 255;
            else if (grey < 0) red = -grey;
            else if (grey < 255) green = grey;
            else green = 255;
            mBlockDisplayGEt[inx0] = transparency | red << 16 | green << 8 | blue;
        }

        void showGradients (Canvas canvas)
        {	// show Ex, Ey, Et, G, G Et
            mBitmapEx.setPixels(mBlockDisplayEx, 0, mBlockWidth, 0, 0, mBlockWidth, mBlockHeight);
            canvas.drawBitmap(mBitmapEx,  0,  mBlockHeight, mPaintBlack);

            mBitmapEy.setPixels(mBlockDisplayEy, 0, mBlockWidth, 0, 0, mBlockWidth, mBlockHeight);
            canvas.drawBitmap(mBitmapEy,  mBlockWidth,  mBlockHeight, mPaintBlack);

            mBitmapEt.setPixels(mBlockDisplayEt, 0, mBlockWidth, 0, 0, mBlockWidth, mBlockHeight);
            canvas.drawBitmap(mBitmapEt,  mBlockWidth,  0, mPaintBlack);

            mBitmapG.setPixels(mBlockDisplayG, 0, mBlockWidth, 0, 0, mBlockWidth, mBlockHeight);
            canvas.drawBitmap(mBitmapG,  0,  mBlockHeight*2, mPaintBlack);

            mBitmapGEt.setPixels(mBlockDisplayGEt, 0, mBlockWidth, 0, 0, mBlockWidth, mBlockHeight);
            canvas.drawBitmap(mBitmapGEt,  mBlockWidth,  mBlockHeight*2, mPaintBlack);
        }

//		draw the given string on top of screen image with black background

        private void drawTextOnBlack (Canvas canvas, String imageStr, int xPos, int yPos, Paint mPaint)
        {
            for (int i =0; i < 4; i++) {	// black background
                int dx = ((i & 1) << 1) - 1;
                int dy = (i & 2) - 1;
                canvas.drawText(imageStr, xPos+dx, yPos+dy, mPaintBlack);
            }
            canvas.drawText(imageStr, xPos, yPos, mPaint);
        }

        int slidingWindow ()
        {	// average A, B, C (and delta T) over the last mStack values
            String TAG="SlidingWindow";
            double aSum=0;
            double bSum=0;
            double cSum=0;

//            if(multiScaleFlag) {//needs to be declared to compile
                double[] aSumScales = new double[scales];
                double[] bSumScales = new double[scales];
                double[] cSumScales = new double[scales];
                Arrays.fill(aSumScales, 0);
                Arrays.fill(bSumScales, 0);
                Arrays.fill(cSumScales, 0);
//            }

            int dSum=0;
            int count=0;
            for (int k = 0; k < mStack; k++) {
                if (aStack[k] != 0 || bStack[k] != 0 || cStack[k] != 0) {
                    aSum += aStack[k];
                    bSum += bStack[k];
                    cSum += cStack[k];
                    dSum += iTStack[k];

                    if(multiScaleFlag) {
                        for (int s = 0; s < scales; s++) {
                            aSumScales[s] += aStackScales[s][k];
                            bSumScales[s] += bStackScales[s][k];
                            cSumScales[s] += cStackScales[s][k];
                            //time is the same for every scale
                        }
                    }
                    count++;
                }
            }
            if (verboseFlag)
                Log.v(TAG, "a " + aSum + " b " + bSum + " c " + cSum + " count " + count);
            if (count > 0) {	// avoid division by zero
                zz[0] = aSum/count; zz[1] = bSum/count; zz[2] = cSum/count;
                zdT = dSum/count;
                if(multiScaleFlag) {
                    for (int s = 0; s < scales; s++) {
                        zz_scales[s][0] = aSumScales[s] / count;
                        zz_scales[s][1] = bSumScales[s] / count;
                        zz_scales[s][2] = cSumScales[s] / count;
                        //time is the same for every scale
                    }
                }

            }
            else {	// no saved values - just use current values
                zz[0] = xx[0]; zz[1] = xx[1]; zz[2] = xx[2];
                zdT = idT;

                if(multiScaleFlag) {
                    for (int s = 0; s < scales; s++) {
                        zz_scales[s][0] = xx_scales[s][0];
                        zz_scales[s][1] = xx_scales[s][1];
                        zz_scales[s][2] = xx_scales[s][2];
                        //time is the same for every scale
                    }
                }
            }

            //minimum TTC is the best
            if(multiScaleFlag) {
                double minTTC = Double.MAX_VALUE;

                scaleChoice = -1; //just in case

                for (int s = 0; s < scales; s++) {
                    if(abs(-1/zz_scales[s][2]) < minTTC)
                    {
                        minTTC = abs(-1/zz_scales[s][2]);
                        scaleChoice = s;
                    }
                }

                zz = zz_scales[scaleChoice].clone();

//                Log.wtf(TAG, "scaleChoice: " + scaleChoice);
            }



            return count;
        }

        // Convert from YUV color to RGB color
        // Luminance (Y) is sampled four times as often as chromaticity (U and V)
        // Array for Luminance comes first,
        // Array for Chromaticity (U and V interlaced) comes after.
        // Each of the U and V values is good for a 2x2 block of Y values
        // Total length of data is 3/2 * height * width in bytes
        // Remember that in java *nothing* is unsigned (except perhaps char)

        public void decodeYUV420SP (int[] rgb, byte[] yuv420sp, int height, int width)
        {
            final int frameSize = width * height;
            for (int j = 0, yp = 0; j < height; j++) {	// step through rows
                int uvp = frameSize + (j >> 1) * width;	// offset to j-th row of chromaticity
                int u = 0, v = 0;
                for (int i = 0; i < width; i++, yp++) {	// step through columns
                    int y = (0xff & ((int) yuv420sp[yp])) - 16;	// luminance
                    if (y < 0) y = 0;
                    if ((i & 1) == 0) {	// even columns only (u, v have lower sampling rate)
                        v = (0xff & yuv420sp[uvp++]) - 128;
                        u = (0xff & yuv420sp[uvp++]) - 128;
                    }

                    int y1192 = 1192 * y;
                    int r = (y1192 + 1634 * v);
                    int g = (y1192 - 833 * v - 400 * u);
                    int b = (y1192 + 2066 * u);

                    if (r < 0) r = 0; else if (r > 0x3FFFF) r = 0x3FFFF;
                    if (g < 0) g = 0; else if (g > 0x3FFFF) g = 0x3FFFF;
                    if (b < 0) b = 0; else if (b > 0x3FFFF) b = 0x3FFFF;

                    // format for RGB integer format data is 0xFFRRGGBB
                    rgb[yp] = transparency | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
                }
            }
        }

        // Convert from YUV color to gray - can ignore chromaticity for speed

        public void decodeYUV420SPGrayscale (int[] rgb, byte[] yuv420sp, int height, int width)
        {	// not used here ?
            final int frameSize = width * height;

            for (int pix = 0; pix < frameSize; pix++) {
                int pixVal = (int) yuv420sp[pix] & 0xff;
                // format for grey scale integer format data is 0xFFRRGGBB where RR = GG = BB
                rgb[pix] = transparency | (pixVal << 16) | (pixVal << 8) | pixVal;
            }
        }

        //		adjust for changes in overall sensitivity or lightlevel from frame	to frame
        public void adjustBrightness (int[] Enew, int[] Eold, int height, int width)
        {
            String TAG="adjustBrightness";
            double sum = 0;
            int ishift;
            double shift;
            for (int k = 0; k < height*width; k++) sum += Enew[k] - Eold[k];
            shift = sum / (height*width);	// normalize
            if (shift >= 0.0) ishift = (int) (shift + 0.5);
            else ishift = - (int) (-shift + 0.5);
            if (ishift > 0) {
                for (int k = 0; k < height*width; k++) Eold[k] += ishift;
            }
            else if (ishift < 0) {
                for (int k = 0; k < height*width; k++) {
                    if (Eold[k] + ishift < 0) Eold[k] = 0;
                    else Eold[k] = Eold[k] + ishift;
                }
            }
            if (verboseFlag) Log.i(TAG, "shifted old by " + ishift);
        }

        public void showHistogram (int[] Enew, int height, int width)
        {
            String TAG="showHistogram";
//			int nMax = 256 * mBlock * mBlock;
            final int nMax = 256 * mBlock ;
            int[] histogram = new int[nMax];

            for (int k=0; k < nMax; k++) histogram[k] = 0;
            for (int inx=0; inx < height*width; inx++) {
                int grey = Enew[inx++];
                if (grey < 0 || grey > nMax-1) {
                    Log.e(TAG, "Grey error " + grey + " i " + inx/width + " j " + inx + (inx/width)*width);
                    break;
                }
                else histogram[grey]++;
            }
            String str;
            for (int h1 = 0; h1 < nMax/16; h1++) {
                str = "" + h1*16 + ": ";
                for (int h2 = 0; h2 < 16; h2++) {
                    str = str + histogram[h1*16+h2] + " ";
                }
                Log.d(TAG, str);
            }
        }

    } // end of nested Class DrawOnTop


// CLASS ----------------------------------------------------------------------

    class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        String TAG="CameraPreview";
        private SurfaceHolder mHolder;

        int mBlockHeight, mBlockWidth;
        int dataLen, viewLen;
        int tmpviewLen[] = new int[scales];//for multiscale

        CameraPreview (Context context, Camera camera, DrawOnTop drawOnTop) {	// creator
            super(context);
            String TAG="CameraPreview";

            mImageHeight = mCameraHeight;
            mImageWidth = mCameraWidth;

            Log.d(TAG, "in creator");
            mDrawOnTop = drawOnTop;	// remember
            mCamera = camera;		// remember camera
            mFinished = false;		// just starting

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
//			mHolder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);		// default
//			following is deprecated setting, but required on Android versions prior to 3.0
//			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated (SurfaceHolder holder) {
            String TAG= "surfaceCreated";
            //	The Surface has been created, now tell the camera where to draw the preview.
            if (mCamera == null) { // camera should be opened by onResume() callback
                Log.e(TAG, "mCamera is null - exiting");	// device not unlocked ?
                Log.e(TAG, "finish()");
                finish();
//				Log.e(TAG, "System.exit(0)");
//				System.exit(0);
            }
            Log.v(TAG, "in surfaceCreated");
            try {
                mCamera.setPreviewDisplay(holder);

                Bitmap.Config colorConfig = Bitmap.Config.RGB_565;	// or ARGB_8888 ?
                Camera.Parameters params = mCamera.getParameters();
                Camera.Size pictureSize = params.getPreviewSize();
                mImageHeight = pictureSize.height;
                mImageWidth = pictureSize.width;
                if (DBG) Log.i(TAG, "Preview bitmap image " + mImageHeight + " x " + mImageWidth);
                mBitmap = Bitmap.createBitmap(mImageWidth, mImageHeight, colorConfig);
                dataLen = 3 * mImageHeight * mImageWidth / 2;	// space for Y + U + V
                mRawYUVData = new byte[dataLen];	// place for copy of raw YUV data
                // assume mImageWidth and mImageHeight are divisible by mBlock
                mBlockHeight = mImageHeight / mBlock;	// size after block averaging
                mBlockWidth = mImageWidth / mBlock;	// size after block averaging
                if (DBG) Log.i(TAG, "Block average image " + mBlockHeight + " x " + mBlockWidth);
                viewLen = mBlockHeight * mBlockWidth;

                //multiscale
                for(int i = 0; i < scales; i++)
                {
                    mBhScales[i] = mImageHeight / mBlockScales[i];
                    mBwScales[i] = mImageWidth / mBlockScales[i];
                    tmpviewLen[i] = mBhScales[i] * mBwScales[i];
                }

                //TODO why isn't viewLen being set correctly for pixel C
                /* mBlockData.length 57600 //WRONG! it's right on nexus 5
                04-10 16:29:36.805 20708-20708/com.example.jbyoung.ttccalculator2 I/onDraw: mBlockHeight 192
                04-10 16:29:36.805 20708-20708/com.example.jbyoung.ttccalculator2 I/onDraw: mBlockWidth 320
                04-10 16:29:36.805 20708-20708/com.example.jbyoung.ttccalculator2 I/onDraw: mBlockWidth*H 61440
                */
//                Log.wtf(TAG, "veiwLen " + viewLen);
//                Log.wtf(TAG, "mBlockHeight " + mBlockHeight);
//                Log.wtf(TAG, "mImageHeight " + mImageHeight);
//                Log.wtf(TAG, "mBlockWidth " + mBlockWidth);

                if (mBlockData == null || mBlockData_Prev == null) { // is initialization  needed ?
                    mBlockData = new int[viewLen];
                    mBlockData_Prev = new int[viewLen];
                    if (DBG) Log.i(TAG, "initialization of BlockData arrays " + viewLen);

                    //multiscale
                    if(multiScaleFlag)
                    {
                        for(int i = 0; i < scales; i++) {
                            mBlockDataScales[i] = new int[tmpviewLen[i]];
                            mBlockDataScales_Prev[i] = new int[tmpviewLen[i]];
                        }
                    }

                }


                if (mBlockDisplayE == null || mBlockDisplayEx == null || mBlockDisplayEy == null || mBlockDisplayEt == null) {
                    mBlockDisplayE = new int[viewLen];
                    mBlockDisplayEx = new int[viewLen];
                    mBlockDisplayEy = new int[viewLen];
                    mBlockDisplayEt = new int[viewLen];
                    mBlockDisplayG = new int[viewLen];
                    mBlockDisplayGEt = new int[viewLen];
                    if (DBG) Log.i(TAG, "initialization of BlockDisplay arrays " + viewLen);
                }
                if (mBitmapE == null || mBitmapEx == null || mBitmapEy == null || mBitmapEt == null || mBitmapG == null || mBitmapGEt == null) {
                    // bitmaps to show intermediate processing steps
                    mBitmapE   = Bitmap.createBitmap(mBlockWidth, mBlockHeight, colorConfig);
                    mBitmapEx  = Bitmap.createBitmap(mBlockWidth, mBlockHeight, colorConfig);
                    mBitmapEy  = Bitmap.createBitmap(mBlockWidth, mBlockHeight, colorConfig);
                    mBitmapEt  = Bitmap.createBitmap(mBlockWidth, mBlockHeight, colorConfig);
                    mBitmapG   = Bitmap.createBitmap(mBlockWidth, mBlockHeight, colorConfig);
                    mBitmapGEt = Bitmap.createBitmap(mBlockWidth, mBlockHeight, colorConfig);
                    if (DBG) Log.i(TAG, "initialization of bitmap arrays " + mBlockHeight + " x " + mBlockWidth);
                }
                if (mRawRGBData == null)
                    mRawRGBData = new int[mImageWidth * mImageHeight];

                if (DBG) Log.i(TAG, "data length " + dataLen + " (should be 3/2 * width * height for NV21 format)" );
                if (DBG) Log.i(TAG, "height " + mImageHeight + " width " + mImageWidth + " -> " + dataLen);

                if (DBG) Log.i(TAG, "PreviewSize " + pictureSize.height + " x " + pictureSize.width);
                int iFormat = params.getPreviewFormat();	// JPEG, NV16, NV21, RGB_565, ... YV12
                if (DBG) Log.i(TAG, pictureFormatString(iFormat));
                int bitsPerPixel = ImageFormat.getBitsPerPixel(iFormat);
                if (DBG) Log.i(TAG, "" + bitsPerPixel + " bits/pixel");
                int pixLen = pictureSize.height*pictureSize.width*bitsPerPixel/8;
                if (pixLen != dataLen)
                    Log.e(TAG, "Preview image buffer size mismatch " + pixLen + " " + dataLen);

                byte[] preBuffer = new byte[dataLen];	// allocate buffer (maybe use more than one ?)
                mCamera.addCallbackBuffer(preBuffer);

                Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        String TAG="onPreviewFrame";
                        if ((mDrawOnTop == null) || mFinished)  return;
                        if (data == null) Log.e(TAG, "data is null");
                        int iimax = mBlockWidth * mBlockHeight;

//                        for (int ii = 0; ii < iimax; ii++)
//                            mBlockData_Prev[ii] = mBlockData[ii];
                        // TODO: speed up copying of image using "System.arraycopy"
//                        System.arraycopy(mBlockData,0,mBlockData_Prev,0,mBlockData.length);
                        // TODO: can we even avoid "arraycopy   "
                        mBlockData_Prev = mBlockData.clone();//use clone? Is this faster?
                        //multiscale
                        if(multiScaleFlag)
                        {
                            for(int i = 0; i < scales; i++) {
                                mBlockDataScales_Prev[i] = mBlockDataScales[i].clone();
                            }
                        }
                        if (traceFlag)
                            Log.d(TAG, "Copying/saving " + iimax + " values " + "(" + mBlockHeight + " x " + mBlockWidth + ")");

                        // Pass YUV data to draw-on-top companion
                        // Log.e("onPreviewFrame", "data " + data + " mRawYUVData " + mRawYUVData);
                        if (data != null) {
                            System.arraycopy(data, 0, mRawYUVData, 0, data.length);    // copy preview image to mRawYUVData
                            if (usingCallBackBufferFlag) mCamera.addCallbackBuffer(data);    // add buffer back in
                            if (traceFlag)
                                if (DBG) Log.i(TAG, "Raw YUV image data length " + data.length);
                        }
                        mPretime = mCurtime;	// save previous time
//						TODO: what is the best way of getting time that corresponds to the image frame ?
//						mCurtime = SystemClock.currentThreadTimeMillis();
//						mCurtime = SystemClock.uptimeMillis();	// or nanoTime() ?
                        mCurtime = System.currentTimeMillis();	// or nanoTime() ?
//						mCurtime = System.nanoTime();	// in nanoseconds, need long
                        mCount++;
                        if (verboseFlag) Log.i(TAG, "Frame " + mCount + " " + mCurtime);
                        int inx = mCount % mStack;
                        aStack[inx] = xx[0];	//save the old values
                        bStack[inx] = xx[1];
                        cStack[inx] = xx[2];
                        if(multiScaleFlag)
                        {
                            for(int s = 0; s < scales; s++)
                            {
                                aStackScales[s][inx] = xx_scales[s][0];
                                bStackScales[s][inx] = xx_scales[s][1];
                                cStackScales[s][inx] = xx_scales[s][2];
                            }
                        }

                        iTStack[inx] = idT;
                        if (traceFlag)
                            Log.v(TAG, "inx " + inx + " xx " + xx[0] + " " + xx[1] + " " + xx[2]);
                        mDrawOnTop.invalidate();
                    }
                };
//				Preview callback used whenever new viewfinder frame is available
                if (usingCallBackBufferFlag)
                    mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
                else mCamera.setPreviewCallback(mPreviewCallback);

            }
            catch (IOException e) {
                mCamera.release();
                mCamera = null;
                Log.e(TAG, "Exception setting camera preview: " + e.getMessage());
                finish();
            }
        }

//		If you want to set a specific size for your camera preview,
//		set this in the surfaceChanged() method (now in onCreate())
//		When setting preview size, you must use values from getSupportedPreviewSizes().
//		Do not set arbitrary values in the setPreviewSize() method.

//		Right now all this is done at "top level" instead
//		when starting, since it shouldn't change...

        public void surfaceChanged (SurfaceHolder holder, int format, int w, int h) {
            String TAG="surfaceChanged";
//			If your preview can change or rotate, take care of those events here.
//			Make sure to stop the preview before resizing or reformatting it.
            Log.v(TAG, "in surfaceChanged");
            if (DBG) Log.i(TAG, "" + h + " x " + w + " " + pictureFormatString(format));
            if (mHolder.getSurface() == null){  // preview surface does not exist
                return;
            }
//			start preview with new settings
            try {
                mCamera.startPreview();
            } catch (Exception e){
                Log.e(TAG, "Error starting camera preview: " + e.getMessage());
            }
            if (DBG) Log.i(TAG, "startPreview " + mCameraHeight + " x " + mCameraWidth + " " + mCameraFrameRate + " fps");
        }

        public void surfaceDestroyed (SurfaceHolder holder) {
            String TAG="surfaceDestroyed";
            // Surface will be destroyed when we return, so stop the preview.
            // Because the CameraDevice object is not a shared resource,
            // it's very important to release it when the activity is paused.
            // NOTE: moved code to onPause()
            //
            Log.v(TAG, "in surfaceDestroyed");
            mFinished = true;
            // Camera should already have been released in onPause(...)
            if (mCamera != null) {
//				mCamera.setPreviewCallback(null);
//				mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
                Log.w(TAG, "stopped preview and released camera");
            }
        }

    } // end of nested Class CameraPreview

} // end of Class TTCCalculator

///////////////////////////////////////////////////////////////////

// color code for LogCat display in Android Studio:

// Log.e ERROR		red
// Log.w WARING		orange
// Log.i INFO		green
// Log.d DEBUG		blue
// Log.v VERBOSE	black

//////////////////////////////////////////////////////////////////

