/*
 * Copyright (C) 2008 ZXing authors
 * Copyright 2011 Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codetr.tanwir.textandlanguageusecamera;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import com.codetr.tanwir.textandlanguageusecamera.camera.CameraManager;
import com.codetr.tanwir.textandlanguageusecamera.camera.ShutterButton;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import org.w3c.dom.Text;

/**
 * This activity opens the camera and does the actual scanning on a background thread. It draws a
 * viewfinder to help the user place the text correctly, shows feedback as the image processing
 * is happening, and then overlays the results when a scan is successful.
 * <p>
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing/
 */
public final class CaptureActivity extends Activity implements SurfaceHolder.Callback,
        ShutterButton.OnShutterButtonListener {
    String variable;
    String equation;

    private static final String TAG = CaptureActivity.class.getSimpleName();

    // Note: These constants will be overridden by any default values defined in preferences.xml.

    public static final String KEY_PLAY_BEEP = "KEY_PLAY_BEEP";
    public static final String KEY_AUTO_FOCUS = "KEY_AUTO_FOCUS";
    public static final String KEY_DISABLE_CONTINUOUS_FOCUS = "KEY_DISABLE_CONTINUOUS_FOCUS";
    public static final String KEY_TOGGLE_LIGHT = "KEY_TOGGLE_LIGHT";
    public static final String KEY_REVERSE_IMAGE = "KEY_REVERSE_IMAGE";

    /**
     * ISO 639-3 language code indicating the default recognition language.
     */
    public static final String DEFAULT_SOURCE_LANGUAGE_CODE = "eng";

    /**
     * ISO 639-1 language code indicating the default target language for translation.
     */
    public static final String DEFAULT_TARGET_LANGUAGE_CODE = "es";

    /**
     * The default OCR engine to use.
     */
    public static final String DEFAULT_OCR_ENGINE_MODE = "Tesseract";

    /**
     * The default page segmentation mode to use.
     */
    public static final String DEFAULT_PAGE_SEGMENTATION_MODE = "Auto";

    /**
     * Whether to use autofocus by default.
     */
    public static final boolean DEFAULT_TOGGLE_AUTO_FOCUS = true;

    /**
     * Whether to initially disable continuous-picture and continuous-video focus modes.
     */
    public static final boolean DEFAULT_DISABLE_CONTINUOUS_FOCUS = true;

    /**
     * Whether to beep by default when the shutter button is pressed.
     */
    public static final boolean DEFAULT_TOGGLE_BEEP = false;

    /**
     * Whether to initially show a looping, real-time OCR display.
     */
    public static final boolean DEFAULT_TOGGLE_CONTINUOUS = false;

    /**
     * Whether to initially reverse the image returned by the camera.
     */
    public static final boolean DEFAULT_TOGGLE_REVERSED_IMAGE = false;


    /**
     * Whether the light should be initially activated by default.
     */
    public static final boolean DEFAULT_TOGGLE_LIGHT = false;


    /**
     * Flag to display the real-time recognition results at the top of the scanning screen.
     */
    private static final boolean CONTINUOUS_DISPLAY_RECOGNIZED_TEXT = true;

    /**
     * Flag to display recognition-related statistics on the scanning screen.
     */
    private static final boolean CONTINUOUS_DISPLAY_METADATA = true;

    /**
     * Flag to enable display of the on-screen shutter button.
     */
    private static final boolean DISPLAY_SHUTTER_BUTTON = true;

    /**
     * Languages for which Cube data is available.
     */
    static final String[] CUBE_SUPPORTED_LANGUAGES = {
            "ara", // Arabic
            "eng", // English
            "hin" // Hindi
    };

    /**
     * Languages that require Cube, and cannot run using Tesseract.
     */
    private static final String[] CUBE_REQUIRED_LANGUAGES = {
            "ara" // Arabic
    };

    //    /**
//     * Resource to use for data file downloads.
//     */
    static final String DOWNLOAD_BASE = "http://tesseract-ocr.googlecode.com/files/";
    //
//    /**
//     * Download filename for orientation and script detection (OSD) data.
//     */
    static final String OSD_FILENAME = "tesseract-ocr-3.01.osd.tar";

    /**
     * Destination filename for orientation and script detection (OSD) data.
     */
    static final String OSD_FILENAME_BASE = "osd.traineddata";


    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private TextView statusViewBottom;
    private TextView statusViewTop;
    private TextView ocrResultView;
    private View cameraButtonView;
    private View resultView;
    private OcrResult lastResult;
    private Bitmap lastBitmap;
    private boolean hasSurface;
    private BeepManager beepManager;
    private TessBaseAPI baseApi; // Java interface for the Tesseract OCR engine
    private String sourceLanguageCodeOcr; // ISO 639-3 language code
    private String sourceLanguageReadable; // Language name, for example, "English"
    private String sourceLanguageCodeTranslation; // ISO 639-1 language code
    private String targetLanguageCodeTranslation; // ISO 639-1 language code
    private String targetLanguageReadable; // Language name, for example, "English"
    private int pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD;
    private int ocrEngineMode = TessBaseAPI.OEM_TESSERACT_ONLY;
    private String characterBlacklist;
    private String characterWhitelist;
    private ShutterButton shutterButton;
    private boolean isTranslationActive; // Whether we want to show translations
    private boolean isContinuousModeActive; // Whether we are doing OCR in continuous mode
    private SharedPreferences prefs;
    private OnSharedPreferenceChangeListener listener;
    private ProgressDialog dialog; // for initOcr - language download & unzip
    private ProgressDialog indeterminateDialog; // also for initOcr - init OCR engine
    private boolean isEngineReady;
    private boolean isPaused;
    private static boolean isFirstLaunch; // True if this is the first time the app is being run

    private Button btnEqual;
    private TextView txtDisplay;

    Handler getHandler() {
        return handler;
    }

    TessBaseAPI getBaseApi() {
        return baseApi;
    }

    CameraManager getCameraManager() {
        return cameraManager;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (isFirstLaunch) {
            setDefaultPreferences();
        }

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.capture);
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        cameraButtonView = findViewById(R.id.camera_button_view);
        resultView = findViewById(R.id.result_view);

        statusViewBottom = (TextView) findViewById(R.id.status_view_bottom);
        registerForContextMenu(statusViewBottom);
        statusViewTop = (TextView) findViewById(R.id.status_view_top);
        registerForContextMenu(statusViewTop);

        handler = null;
        lastResult = null;
        hasSurface = false;
        beepManager = new BeepManager(this);

        // Camera shutter button
        if (DISPLAY_SHUTTER_BUTTON) {
            shutterButton = (ShutterButton) findViewById(R.id.shutter_button);
            shutterButton.setOnShutterButtonListener(this);
        }

        ocrResultView = (TextView) findViewById(R.id.ocr_result_text_view);
        registerForContextMenu(ocrResultView);

        cameraManager = new CameraManager(getApplication());
        viewfinderView.setCameraManager(cameraManager);

        // Set listener to change the size of the viewfinder rectangle.
        viewfinderView.setOnTouchListener(new View.OnTouchListener() {
            int lastX = -1;
            int lastY = -1;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = -1;
                        lastY = -1;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int currentX = (int) event.getX();
                        int currentY = (int) event.getY();

                        try {
                            Rect rect = cameraManager.getFramingRect();

                            final int BUFFER = 50;
                            final int BIG_BUFFER = 60;
                            if (lastX >= 0) {
                                // Adjust the size of the viewfinder rectangle. Check if the touch event occurs in the corner areas first, because the regions overlap.
                                if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) || (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
                                        && ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) || (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
                                    // Top left corner: adjust both top and left sides
                                    cameraManager.adjustFramingRect(2 * (lastX - currentX), 2 * (lastY - currentY));
                                    viewfinderView.removeResultText();
                                } else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) || (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER))
                                        && ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) || (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
                                    // Top right corner: adjust both top and right sides
                                    cameraManager.adjustFramingRect(2 * (currentX - lastX), 2 * (lastY - currentY));
                                    viewfinderView.removeResultText();
                                } else if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) || (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
                                        && ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) || (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
                                    // Bottom left corner: adjust both bottom and left sides
                                    cameraManager.adjustFramingRect(2 * (lastX - currentX), 2 * (currentY - lastY));
                                    viewfinderView.removeResultText();
                                } else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) || (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER))
                                        && ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) || (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
                                    // Bottom right corner: adjust both bottom and right sides
                                    cameraManager.adjustFramingRect(2 * (currentX - lastX), 2 * (currentY - lastY));
                                    viewfinderView.removeResultText();
                                } else if (((currentX >= rect.left - BUFFER && currentX <= rect.left + BUFFER) || (lastX >= rect.left - BUFFER && lastX <= rect.left + BUFFER))
                                        && ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
                                    // Adjusting left side: event falls within BUFFER pixels of left side, and between top and bottom side limits
                                    cameraManager.adjustFramingRect(2 * (lastX - currentX), 0);
                                    viewfinderView.removeResultText();
                                } else if (((currentX >= rect.right - BUFFER && currentX <= rect.right + BUFFER) || (lastX >= rect.right - BUFFER && lastX <= rect.right + BUFFER))
                                        && ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
                                    // Adjusting right side: event falls within BUFFER pixels of right side, and between top and bottom side limits
                                    cameraManager.adjustFramingRect(2 * (currentX - lastX), 0);
                                    viewfinderView.removeResultText();
                                } else if (((currentY <= rect.top + BUFFER && currentY >= rect.top - BUFFER) || (lastY <= rect.top + BUFFER && lastY >= rect.top - BUFFER))
                                        && ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
                                    // Adjusting top side: event falls within BUFFER pixels of top side, and between left and right side limits
                                    cameraManager.adjustFramingRect(0, 2 * (lastY - currentY));
                                    viewfinderView.removeResultText();
                                } else if (((currentY <= rect.bottom + BUFFER && currentY >= rect.bottom - BUFFER) || (lastY <= rect.bottom + BUFFER && lastY >= rect.bottom - BUFFER))
                                        && ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
                                    // Adjusting bottom side: event falls within BUFFER pixels of bottom side, and between left and right side limits
                                    cameraManager.adjustFramingRect(0, 2 * (currentY - lastY));
                                    viewfinderView.removeResultText();
                                }
                            }
                        } catch (NullPointerException e) {
                            Log.e(TAG, "Framing rect not available", e);
                        }
                        v.invalidate();
                        lastX = currentX;
                        lastY = currentY;
                        return true;
                    case MotionEvent.ACTION_UP:
                        lastX = -1;
                        lastY = -1;
                        return true;
                }
                return false;
            }
        });

        isEngineReady = false;


//        hasil calculator
        btnEqual = (Button) findViewById(R.id.btnEqual);
        btnEqual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                txtDisplay = (TextView) findViewById(R.id.txtDisplay);
                // Read the expression
                String txt = ocrResultView.getText().toString();

                if(!(txt.contains("="))){
                    // Create an Expression (A class from exp4j library)
                    Expression expression = new ExpressionBuilder(txt).build();
                    // Calculate the result and display
                    double result = expression.evaluate();
                    txtDisplay.setText(Double.toString(result));

                } else {
                    String equation = presolve(txt);
                    equation = newsolve(equation);
                    txtDisplay.setText(equation);
                }
            }
        });
    }

    String presolve(String equation){
        String[] parts = equation.split("=");
        ArrayList<String> a = split(parts[0]);
        ArrayList<String> newa = new ArrayList<>();
        String temp;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).contains("/") || a.get(i).contains("*")){
                if (!(a.get(i).contains("x") || a.get(i).contains("X"))) {
                    Expression expression = new ExpressionBuilder(a.get(i)).build();
                    newa.add(Double.toString(expression.evaluate()));
                }
                else{
                    newa.add(a.get(i));
                }
            }
            else{
                newa.add(a.get(i));
            }

        }


        ArrayList<String> b = split(parts[1]);
        ArrayList<String> newb = new ArrayList<>();
        for (int i = 0; i < b.size(); i++) {
            if (b.get(i).contains("/") || b.get(i).contains("*")){
                if (!(b.get(i).contains("x") || b.get(i).contains("X"))) {
                    Expression expression = new ExpressionBuilder(b.get(i)).build();
                    newb.add(Double.toString(expression.evaluate()));
                }
                else{
                    newb.add(b.get(i));
                }
            }
            else{
                newb.add(b.get(i));
            }

        }




        return getSolution(newa,newb).replace(" ", "");
    }


    // Function to solve
// the given equation

    String newsolve(String equation) {
        String[] parts = equation.split("=", 2);
        ArrayList<String> a = split(parts[0]);
        ArrayList<String> b = split(parts[1]);
        double numvar=0;
        double num=0;
        double result;
        String tempo="";
        String temp="";
        String prueba="";

        for (int i = 0; i < a.size(); i++) {
            if ((a.get(i).contains("x") || a.get(i).contains("X"))) {
                tempo=a.get(i).replace("x", "");
                tempo=tempo.replace("X", "");

                if (tempo.equals("+") || tempo.equals("")){
                    tempo="+1";
                    //prueba=prueba+temp;
                }
                else if (tempo.equals("-")) {
                    tempo="-1";
                    //prueba=prueba+temp;
                }
                numvar=numvar+Double.parseDouble(tempo);
                //prueba = prueba + temp;


            }
            else{

                num=num+Double.parseDouble(a.get(i));
                prueba=prueba+a.get(i);
            }
        }

        for (int i = 0; i < b.size(); i++) {
            if ((b.get(i).contains("x") || b.get(i).contains("X"))) {
                temp=b.get(i).replace("x", "");
                temp=temp.replace("X", "");

                if (temp.equals("+") || temp.equals("")){
                    temp="+1";
                    //prueba=prueba+temp;
                }
                else if (temp.equals("-")) {
                    temp="-1";
                    //prueba=prueba+temp;
                }
                numvar=numvar-Double.parseDouble(temp);
                //prueba = prueba + temp;


            }
            else{
                num=num-Double.parseDouble(b.get(i));
                //prueba=prueba+b.get(i);
            }
        }

        if (numvar==0) {
            if (num==0){
                return("Soluciones infinitas");
            }
            else {
                return("Sin solucion");
            }

        }
        else{
            result=-1*(num/numvar);
            return (""+result);
        }



    }




    @Override
    protected void onResume() {
        super.onResume();
        resetStatusView();

        String previousSourceLanguageCodeOcr = sourceLanguageCodeOcr;
        int previousOcrEngineMode = ocrEngineMode;

        retrievePreferences();

        // Set up the camera preview surface.
        surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        surfaceHolder = surfaceView.getHolder();
        if (!hasSurface) {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        // Comment out the following block to test non-OCR functions without an SD card

        // Do OCR engine initialization, if necessary
        boolean doNewInit = (baseApi == null) || !sourceLanguageCodeOcr.equals(previousSourceLanguageCodeOcr) ||
                ocrEngineMode != previousOcrEngineMode;
        if (doNewInit) {
//       Initialize the OCR engine
            File storageDirectory = getStorageDirectory();
            if (storageDirectory != null) {
                initOcrEngine(storageDirectory, sourceLanguageCodeOcr, sourceLanguageReadable);
            }
        } else {
            // We already have the engine initialized, so just start the camera.
            resumeOCR();
        }
    }

    /**
     *Split a given expression into its components.
     *
     * @param equation The equation to split
     * @return ArrayList of the components in equation
     */
    public ArrayList<String> split(String equation) {
        String expression = "";
        ArrayList<String> splitedEquation = new ArrayList();
        boolean isInsideBracket = false;

        for (int i = 0; i < equation.length(); i++) {
            String chr = Character.toString(equation.charAt(i));


            if ((chr.equals("+") || chr.equals("-")) && i != 0 && !isInsideBracket) {
                splitedEquation.add(expression);
                expression = chr;

            } else {
                expression +=chr;
            }

            if (chr.equals("(")) {
                isInsideBracket = true;
            } else if (chr.equals(")")) {
                isInsideBracket = false;
            }
        }
        splitedEquation.add(expression);

        return splitedEquation;
    }

    /**
     * Checks if an expression contains a bracket.
     *
     * @param expression
     * @return boolean
     */
    public boolean containsBracket(String expression) {
        return  expression.contains("(");
    }

    public String getExprInBracket(String expr) {
        String exprInBracket = "";
        for (int i =0; i < expr.length() - 1; i++) {
            exprInBracket +=expr.charAt(i);
        }

        return exprInBracket;
    }

    /**
     * It expands an expression with bracket e.g it turns 2(5x-8) => 10x - 16
     *
     * @param expression The expression to expand
     * @return a string with the evaluated form of the expression
     */

    /**
     * It expands an expression with bracket e.g it turns 2(5x-8) => 10x - 16
     *
     * @param expression The expression to expand
     * @return a string with the evaluated form of the expression
     */
    public ArrayList<String> expand(String expression) {

        String[] expr = expression.split("[(]");

        int multiplier = expr[0].equals("-") || expr[0].equals("+") ? Integer.parseInt(expr[0] + "1") : Integer.parseInt(expr[0]);
        ArrayList<String> result = new ArrayList<>();

        ArrayList<String> exprInBracket = split(getExprInBracket(expr[1]));

        for (int i =0; i < exprInBracket.size(); i++) {
            String elem = exprInBracket.get(i);
            try {
                Integer constant = multiplier * Integer.parseInt(elem);
                result.add(constant.toString());
            } catch(Exception e) {
                Integer newCoefficient = getCoefficient(elem) * multiplier;
                Log.d("VARIABLE", variable);
                result.add(newCoefficient.toString() + variable);
            }
        }

        return result;
    }

    /**
     * It removes and evaluates components with bracket. e.g It turns ['2x', 2(x-8)] to ['2x', '2x', '-8']
     *
     * @param equationComponents equation components
     * @return
     */
    public ArrayList<String> openBracket(ArrayList<String> equationComponents) {
        ArrayList<String> eqtComponents = new ArrayList<>();
        for (int i = 0; i < equationComponents.size(); i++) {

            if (containsBracket(equationComponents.get(i))) {
                eqtComponents.addAll(expand(equationComponents.get(i)));
            } else {
                eqtComponents.add(equationComponents.get(i));
            }

        }

        return eqtComponents;
    }

    public String getSolutionVar(String var) {
        char firstChar = var.charAt(0);
        if (Character.toString(firstChar).equals("+")) {
            return var.replace("+", "+ ");
        } else if (Character.toString(firstChar).equals("-")) {
            return var.replace("-", "- ");
        } else {
            return "+ " + var;
        }
    }

    /**
     * It merges the right and left hand side of the equation components
     *
     * @param leftHandSide(List) The left hand side of the equation in a list
     * @param rightHandSide(List) Contains the components on the right hand side of the equation in a list
     *
     * @return String The string that contain the simplified equation
     */
    public String getSolution(ArrayList<String> leftHandSide, ArrayList<String> rightHandSide) {
        String leftHandSideSolution = "";
        String rightHandSideSolution = "";
        System.out.println(leftHandSide);

        for (int i=0; i < leftHandSide.size(); i++) {
            if (i == 0) {
                leftHandSideSolution = leftHandSide.get(0);
            } else {
                leftHandSideSolution = leftHandSideSolution + " " + getSolutionVar(leftHandSide.get(i));
            }
        }

        for (int i=0; i < rightHandSide.size(); i++) {
            if (i == 0) {
                rightHandSideSolution = rightHandSide.get(0);
            } else {
                rightHandSideSolution = rightHandSideSolution + " " + getSolutionVar(rightHandSide.get(i));
            }
        }
        return leftHandSideSolution + " = " + rightHandSideSolution;
    }

    public String changeVariableSign(String variable) {
        char firstChar = variable.charAt(0);
        if (Character.toString(firstChar).equals("+")){
            return variable.replace("+", "-");
        } else if (Character.toString(firstChar).equals("-")) {
            return variable.replace("-", "+");
        } else {
            return "-" + variable;
        }
    }

    /**
     * It Collect like terms.
     *
     * @param leftHandSide The left hand side of the equation
     * @param rightHandSide The right hand side of the equation
     * @return Array with variables and constants seperately
     */
    public ArrayList<String>[] collectLikeTerms(ArrayList<String> leftHandSide, ArrayList<String> rightHandSide) {
        ArrayList<String> variables = new ArrayList<>();
        ArrayList<String> constants = new ArrayList<>();

        for (int i = 0; i < leftHandSide.size(); i++) {
            String elem = leftHandSide.get(i);
            try {
                Integer constant =  -1 * Integer.parseInt(elem);
                constants.add(constant.toString());
            } catch(Exception e) {
                variables.add(elem);
            }
        }

        for (int j = 0; j < rightHandSide.size(); j++) {
            String elem = rightHandSide.get(j);
            try {
                Integer constant =  Integer.parseInt(elem);
                constants.add(constant.toString());
            } catch(Exception e) {
                variables.add(changeVariableSign(elem));
            }
        }

        ArrayList<String>[] result = new ArrayList[2];
        result[0] = variables;
        result[1] = constants;
        return result;
    }

    /**
     * Gets the coefficient of a variable (as the name implies)
     *
     * @param variable
     * @return int the coefficient of the variable
     */
    public static int getCoefficient(String variable){
        String coefficient = "";
        if(variable.length() == 1) return 1;
        else if(variable.length() == 2 && variable.charAt(0) == '-') return -1;

        for(int i = 0; i < variable.length(); i++){
            if(Character.isDigit(variable.charAt(i)))coefficient+=variable.charAt(i);
        }
        if(variable.charAt(0) == '-')return Integer.parseInt("-" + coefficient);
        return Integer.parseInt(coefficient);
    }

    /**
     * It simplify an expression. eg from [2x, +3x] into 5x
     *
     * @param expression The components in of an expression in an array e.g [2x, 4x, -5x]
     * @return String that holds the simplified expression
     */
    public String simplifyExpression(ArrayList<String> expression) {
        Integer coefficient = 0;
        for (int i = 0; i < expression.size(); i++) {
            coefficient += getCoefficient(expression.get(i));
        }

        if (coefficient == 1) return variable;
        if (coefficient == -1) return "-" + variable;

        return coefficient.toString(coefficient) + variable;
    }

    /**
     * Add up all the constants
     *
     * @param constants ArrayList of constants
     * @return String
     */

    public String simplifyConstants(ArrayList<String> constants) {

        Integer constantSum = 0;
        for (int i = 0; i < constants.size(); i++) {
            constantSum += Integer.parseInt(constants.get(i));
        }

        return constantSum.toString();
    }


    public String solve(String equation) {

        equation = equation.replaceAll("\\s+",""); //removing all spaces from equation

        //Paso 1 terminado

        String[] divEquation = equation.split("=");

        String leftHandSide = divEquation[0]; // The left handside of the equation
        String rightHandSide = divEquation[1];
        ArrayList<String> leftHandSideComps = split(leftHandSide);
        ArrayList<String> rightHandSideComps = split(rightHandSide);



        ArrayList<String>[] likeTerms = collectLikeTerms(leftHandSideComps, rightHandSideComps);

        leftHandSideComps = likeTerms[0]; // Now holds the variables.
        rightHandSideComps = likeTerms[1]; // Now holds the constants.

        equation=getSolution(leftHandSideComps, rightHandSideComps);
        //Paso 3

        String variableSum = simplifyExpression(leftHandSideComps);
        String constantSum = simplifyConstants(rightHandSideComps);
        Integer coef = getCoefficient(variableSum);

        equation=variableSum + " = " + constantSum;
        //paso4


        if (variableSum.equals(variable)) {
            equation=variable + " = " + constantSum;
            //Ultimo paso si ya se despejo
            return equation;
        }

        if (coef == -1) {
            constantSum = Integer.toString(Integer.parseInt(constantSum) * -1);
            equation= variable + " = " + constantSum;
            //ultimo paso
            return equation;
        }
        float constant = Float.parseFloat(constantSum)/coef;

        DecimalFormat df = new DecimalFormat("0.00");

        equation=variableSum + "/" + coef.toString() + " = " + constantSum + "/" + coef.toString();

        if (coef == 0) {
            equation=variable + " = " + "NAN or undefined";
            return equation;
        }


        return variable + " = " + df.format(constant);
    }

    /**
     * Method to start or restart recognition after the OCR engine has been initialized,
     * or after the app regains focus. Sets state related settings and OCR engine parameters,
     * and requests camera initialization.
     */
    void resumeOCR() {
        Log.d(TAG, "resumeOCR()");

        // This method is called when Tesseract has already been successfully initialized, so set
        // isEngineReady = true here.
        isEngineReady = true;

        isPaused = false;

        if (handler != null) {
            handler.resetState();
        }
        if (baseApi != null) {
            baseApi.setPageSegMode(pageSegmentationMode);
            baseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, characterBlacklist);
            baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, characterWhitelist);
        }

        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceHolder);
        }
    }

    /**
     * Called when the shutter button is pressed in continuous mode.
     */
    void onShutterButtonPressContinuous() {
        isPaused = true;
        handler.stop();
        beepManager.playBeepSoundAndVibrate();
        if (lastResult != null) {
            handleOcrDecode(lastResult);
        } else {
            Toast toast = Toast.makeText(this, "Kata yang diambil masih kosong", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();
            resumeContinuousDecoding();
        }
    }

    /**
     * Called to resume recognition after translation in continuous mode.
     */
    @SuppressWarnings("unused")
    void resumeContinuousDecoding() {
        isPaused = false;
        resetStatusView();
        setStatusViewForContinuous();
        DecodeHandler.resetDecodeState();
        handler.resetState();
        if (shutterButton != null && DISPLAY_SHUTTER_BUTTON) {
            shutterButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated()");

        if (holder == null) {
            Log.e(TAG, "surfaceCreated gave us a null surface");
        }

        // Only initialize the camera if the OCR engine is ready to go.
        if (!hasSurface && isEngineReady) {
            Log.d(TAG, "surfaceCreated(): calling initCamera()...");
            initCamera(holder);
        }
        hasSurface = true;
    }

    /**
     * Initializes the camera and starts the handler to begin previewing.
     */
    private void initCamera(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "initCamera()");
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        try {

            // Open and initialize the camera
            cameraManager.openDriver(surfaceHolder);

            // Creating the handler starts the preview, which can also throw a RuntimeException.
            handler = new CaptureActivityHandler(this, cameraManager, isContinuousModeActive);

        } catch (IOException ioe) {
            showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
        }
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
        }

        // Stop using the camera, to avoid conflicting with other camera-based apps
        cameraManager.closeDriver();

        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    void stopHandler() {
        if (handler != null) {
            handler.stop();
        }
    }

    @Override
    protected void onDestroy() {
        if (baseApi != null) {
            baseApi.end();
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {

            // First check if we're paused in continuous mode, and if so, just unpause.
            if (isPaused) {
                Log.d(TAG, "only resuming continuous recognition, not quitting...");
                resumeContinuousDecoding();
                return true;
            }

            // Exit the app if we're not viewing an OCR result.
            if (lastResult == null) {
                setResult(RESULT_CANCELED);
                finish();
                return true;
            } else {
                // Go back to previewing in regular OCR mode.
                resetStatusView();
                if (handler != null) {
                    handler.sendEmptyMessage(R.id.restart_preview);
                }
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            if (isContinuousModeActive) {
                onShutterButtonPressContinuous();
            } else {
                handler.hardwareShutterButtonClick();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_FOCUS) {
            // Only perform autofocus if user is not holding down the button.
            if (event.getRepeatCount() == 0) {
                cameraManager.requestAutoFocus(500L);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    /**
     * Sets the necessary language code values for the given OCR language.
     */
    private boolean setSourceLanguage(String languageCode) {
        sourceLanguageCodeOcr = languageCode;

        return true;
    }

    /**
     * Sets the necessary language code values for the translation target language.
     */
    private boolean setTargetLanguage(String languageCode) {
        targetLanguageCodeTranslation = languageCode;

        return true;
    }

    /**
     * Finds the proper location on the SD card where we can save files.
     */
    private File getStorageDirectory() {
        //Log.d(TAG, "getStorageDirectory(): API level is " + Integer.valueOf(android.os.Build.VERSION.SDK_INT));

        String state = null;
        try {
            state = Environment.getExternalStorageState();
        } catch (RuntimeException e) {
            Log.e(TAG, "Is the SD card visible?", e);
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable.");
        }

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {

            // We can read and write the media
            //    	if (Integer.valueOf(android.os.Build.VERSION.SDK_INT) > 7) {
            // For Android 2.2 and above

            try {
                return getExternalFilesDir(Environment.MEDIA_MOUNTED);
            } catch (NullPointerException e) {
                // We get an error here if the SD card is visible, but full
                Log.e(TAG, "External storage is unavailable");
                showErrorMessage("Error", "Required external storage (such as an SD card) is full or unavailable.");
            }

            //        } else {
            //          // For Android 2.1 and below, explicitly give the path as, for example,
            //          // "/mnt/sdcard/Android/data/edu.sfsu.cs.orange.ocr/files/"
            //          return new File(Environment.getExternalStorageDirectory().toString() + File.separator +
            //                  "Android" + File.separator + "data" + File.separator + getPackageName() +
            //                  File.separator + "files" + File.separator);
            //        }

        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // We can only read the media
            Log.e(TAG, "External storage is read-only");
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable for data storage.");
        } else {
            // Something else is wrong. It may be one of many other states, but all we need
            // to know is we can neither read nor write
            Log.e(TAG, "External storage is unavailable");
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable or corrupted.");
        }
        return null;
    }

    /**
     * Requests initialization of the OCR engine with the given parameters.
     *
     * @param storageRoot  Path to location of the tessdata directory to use
     * @param languageCode Three-letter ISO 639-3 language code for OCR
     * @param languageName Name of the language for OCR, for example, "English"
     */
    private void initOcrEngine(File storageRoot, String languageCode, String languageName) {
        isEngineReady = false;

        // Set up the dialog box for the thermometer-style download progress indicator
        if (dialog != null) {
            dialog.dismiss();
        }
        dialog = new ProgressDialog(this);

        // If we have a language that only runs using Cube, then set the ocrEngineMode to Cube
        if (ocrEngineMode != TessBaseAPI.OEM_CUBE_ONLY) {
            for (String s : CUBE_REQUIRED_LANGUAGES) {
                if (s.equals(languageCode)) {
                    ocrEngineMode = TessBaseAPI.OEM_CUBE_ONLY;
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    prefs.edit().putString("KEY_OCR_ENGINE_MODE", getOcrEngineModeName()).commit();
                }
            }
        }

        // If our language doesn't support Cube, then set the ocrEngineMode to Tesseract
        if (ocrEngineMode != TessBaseAPI.OEM_TESSERACT_ONLY) {
            boolean cubeOk = false;
            for (String s : CUBE_SUPPORTED_LANGUAGES) {
                if (s.equals(languageCode)) {
                    cubeOk = true;
                }
            }
            if (!cubeOk) {
                ocrEngineMode = TessBaseAPI.OEM_TESSERACT_ONLY;
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putString("KEY_OCR_ENGINE_MODE", getOcrEngineModeName()).commit();
            }
        }

        // Display the name of the OCR engine we're initializing in the indeterminate progress dialog box
        indeterminateDialog = new ProgressDialog(this);
        indeterminateDialog.setTitle("Tunggu Sebentar");
        String ocrEngineModeName = getOcrEngineModeName();
        if (ocrEngineModeName.equals("Both")) {
            indeterminateDialog.setMessage("Pemasangan data " + languageName + "...");
        } else {
            indeterminateDialog.setMessage("Pemasangan data " + languageName + "...");
        }
        indeterminateDialog.setCancelable(false);
        indeterminateDialog.show();

        if (handler != null) {
            handler.quitSynchronously();
        }

        // Disable continuous mode if we're using Cube. This will prevent bad states for devices
        // with low memory that crash when running OCR with Cube, and prevent unwanted delays.
        if (ocrEngineMode == TessBaseAPI.OEM_CUBE_ONLY || ocrEngineMode == TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED) {
            Log.d(TAG, "Disabling continuous preview");
            isContinuousModeActive = false;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean("KEY_CONTINUOUS_PREVIEW", false);
        }

        // Start AsyncTask to install language data and init OCR
        baseApi = new TessBaseAPI();
        new OcrInitAsyncTask(this, baseApi, dialog, indeterminateDialog, languageCode, languageName, ocrEngineMode)
                .execute(storageRoot.toString());
    }

    /**
     * Displays information relating to the result of OCR, and requests a translation if necessary.
     *
     * @param ocrResult Object representing successful OCR results
     * @return True if a non-null result was received for OCR
     */
    boolean handleOcrDecode(OcrResult ocrResult) {
        lastResult = ocrResult;

        // Test whether the result is null
        if (ocrResult.getText() == null || ocrResult.getText().equals("")) {
            Toast toast = Toast.makeText(this, "Kata yang diambil masih kosong", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();
            return false;
        }

        // Turn off capture-related UI elements
        btnEqual.setVisibility(View.VISIBLE);

        shutterButton.setVisibility(View.GONE);
        statusViewBottom.setVisibility(View.GONE);
        statusViewTop.setVisibility(View.GONE);
        cameraButtonView.setVisibility(View.GONE);
        viewfinderView.setVisibility(View.GONE);
        resultView.setVisibility(View.VISIBLE);

        ImageView bitmapImageView = (ImageView) findViewById(R.id.image_view);
        lastBitmap = ocrResult.getBitmap();
        if (lastBitmap == null) {
            bitmapImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(),
                    R.mipmap.ic_launcher));
        } else {
            bitmapImageView.setImageBitmap(lastBitmap);
        }

        // Display the recognized text
        TextView ocrResultTextView = (TextView) findViewById(R.id.ocr_result_text_view);
        ocrResultTextView.setText(ocrResult.getText());
        // Crudely scale betweeen 22 and 32 -- bigger font for shorter text
        int scaledSize = Math.max(22, 32 - ocrResult.getText().length() / 4);
        ocrResultTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);

        return true;
    }

    /**
     * Displays information relating to the results of a successful real-time OCR request.
     *
     * @param ocrResult Object representing successful OCR results
     */
    void handleOcrContinuousDecode(OcrResult ocrResult) {

        lastResult = ocrResult;

        // Send an OcrResultText object to the ViewfinderView for text rendering
        viewfinderView.addResultText(new OcrResultText(ocrResult.getText(),
                ocrResult.getWordConfidences(),
                ocrResult.getMeanConfidence(),
                ocrResult.getBitmapDimensions(),
                ocrResult.getRegionBoundingBoxes(),
                ocrResult.getTextlineBoundingBoxes(),
                ocrResult.getStripBoundingBoxes(),
                ocrResult.getWordBoundingBoxes(),
                ocrResult.getCharacterBoundingBoxes()));

        Integer meanConfidence = ocrResult.getMeanConfidence();

        if (CONTINUOUS_DISPLAY_RECOGNIZED_TEXT) {
            // Display the recognized text on the screen
            statusViewTop.setText(ocrResult.getText());
            int scaledSize = Math.max(22, 32 - ocrResult.getText().length() / 4);
            statusViewTop.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);
            statusViewTop.setTextColor(Color.BLACK);
            statusViewTop.setBackgroundResource(R.color.status_top_text_background);

            statusViewTop.getBackground().setAlpha(meanConfidence * (255 / 100));
        }

        if (CONTINUOUS_DISPLAY_METADATA) {
            // Display recognition-related metadata at the bottom of the screen
            long recognitionTimeRequired = ocrResult.getRecognitionTimeRequired();
            statusViewBottom.setTextSize(14);
            statusViewBottom.setText("OCR: " + sourceLanguageReadable + " - Mean confidence: " +
                    meanConfidence.toString() + " - Time required: " + recognitionTimeRequired + " ms");
        }
    }

    /**
     * Version of handleOcrContinuousDecode for failed OCR requests. Displays a failure message.
     *
     * @param obj Metadata for the failed OCR request.
     */
    void handleOcrContinuousDecode(OcrResultFailure obj) {
        lastResult = null;
        viewfinderView.removeResultText();

        // Reset the text in the recognized text box.
        statusViewTop.setText("");

        if (CONTINUOUS_DISPLAY_METADATA) {
            // Color text delimited by '-' as red.
            statusViewBottom.setTextSize(14);
            CharSequence cs = setSpanBetweenTokens("OCR: " + sourceLanguageReadable + " - OCR failed - Time required: "
                    + obj.getTimeRequired() + " ms", "-", new ForegroundColorSpan(0xFFFF0000));
            statusViewBottom.setText(cs);
        }
    }

    /**
     * Given either a Spannable String or a regular String and a token, apply
     * the given CharacterStyle to the span between the tokens.
     * <p>
     * NOTE: This method was adapted from:
     * http://www.androidengineer.com/2010/08/easy-method-for-formatting-android.html
     * <p>
     * <p>
     * For example, {@code setSpanBetweenTokens("Hello ##world##!", "##", new
     * ForegroundColorSpan(0xFFFF0000));} will return a CharSequence {@code
     * "Hello world!"} with {@code world} in red.
     */
    private CharSequence setSpanBetweenTokens(CharSequence text, String token,
                                              CharacterStyle... cs) {
        // Start and end refer to the points where the span will apply
        int tokenLen = token.length();
        int start = text.toString().indexOf(token) + tokenLen;
        int end = text.toString().indexOf(token, start);

        if (start > -1 && end > -1) {
            // Copy the spannable string to a mutable spannable string
            SpannableStringBuilder ssb = new SpannableStringBuilder(text);
            for (CharacterStyle c : cs)
                ssb.setSpan(c, start, end, 0);
            text = ssb;
        }
        return text;
    }


    /**
     * Resets view elements.
     */
    private void resetStatusView() {
        resultView.setVisibility(View.GONE);
        if (CONTINUOUS_DISPLAY_METADATA) {
            statusViewBottom.setText("");
            statusViewBottom.setTextSize(14);
            statusViewBottom.setTextColor(getResources().getColor(R.color.status_text));
            statusViewBottom.setVisibility(View.VISIBLE);
        }
        if (CONTINUOUS_DISPLAY_RECOGNIZED_TEXT) {
            statusViewTop.setText("");
            statusViewTop.setTextSize(14);
            statusViewTop.setVisibility(View.VISIBLE);
        }
        viewfinderView.setVisibility(View.VISIBLE);
        cameraButtonView.setVisibility(View.VISIBLE);
        if (DISPLAY_SHUTTER_BUTTON) {
            shutterButton.setVisibility(View.VISIBLE);
        }
        lastResult = null;
        viewfinderView.removeResultText();
    }


    /**
     * Displays an initial message to the user while waiting for the first OCR request to be
     * completed after starting realtime OCR.
     */
    void setStatusViewForContinuous() {
        viewfinderView.removeResultText();
        if (CONTINUOUS_DISPLAY_METADATA) {
            statusViewBottom.setText("OCR: " + sourceLanguageReadable + " - waiting for OCR...");
        }
    }

    @SuppressWarnings("unused")
    void setButtonVisibility(boolean visible) {
        if (shutterButton != null && visible == true && DISPLAY_SHUTTER_BUTTON) {
            shutterButton.setVisibility(View.VISIBLE);
        } else if (shutterButton != null) {
            shutterButton.setVisibility(View.GONE);
        }
    }

    /**
     * Enables/disables the shutter button to prevent double-clicks on the button.
     *
     * @param clickable True if the button should accept a click
     */
    void setShutterButtonClickable(boolean clickable) {
        shutterButton.setClickable(clickable);
    }

    /**
     * Request the viewfinder to be invalidated.
     */
    void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    @Override
    public void onShutterButtonClick(ShutterButton b) {
        if (isContinuousModeActive) {
            onShutterButtonPressContinuous();
        } else {
            if (handler != null) {
                handler.shutterButtonClick();
            }
        }
    }

    @Override
    public void onShutterButtonFocus(ShutterButton b, boolean pressed) {
        requestDelayedAutoFocus();
    }

    /**
     * Requests autofocus after a 350 ms delay. This delay prevents requesting focus when the user
     * just wants to click the shutter button without focusing. Quick button press/release will
     * trigger onShutterButtonClick() before the focus kicks in.
     */
    private void requestDelayedAutoFocus() {
        // Wait 350 ms before focusing to avoid interfering with quick button presses when
        // the user just wants to take a picture without focusing.
        cameraManager.requestAutoFocus(350L);
    }

    /**
     * Returns a string that represents which OCR engine(s) are currently set to be run.
     *
     * @return OCR engine mode
     */
    String getOcrEngineModeName() {
        String ocrEngineModeName = "";
        String[] ocrEngineModes = getResources().getStringArray(R.array.ocrenginemodes);
        if (ocrEngineMode == TessBaseAPI.OEM_TESSERACT_ONLY) {
            ocrEngineModeName = ocrEngineModes[0];
        } else if (ocrEngineMode == TessBaseAPI.OEM_CUBE_ONLY) {
            ocrEngineModeName = ocrEngineModes[1];
        } else if (ocrEngineMode == TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED) {
            ocrEngineModeName = ocrEngineModes[2];
        }
        return ocrEngineModeName;
    }

    /**
     * Gets values from shared preferences and sets the corresponding data members in this activity.
     */
    private void retrievePreferences() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Retrieve from preferences, and set in this Activity, the language preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        setSourceLanguage(prefs.getString("KEY_SOURCE_LANGUAGE_PREFERENCE", CaptureActivity.DEFAULT_SOURCE_LANGUAGE_CODE));
        setTargetLanguage(prefs.getString("KEY_TARGET_LANGUAGE_PREFERENCE", CaptureActivity.DEFAULT_TARGET_LANGUAGE_CODE));
        isTranslationActive = prefs.getBoolean("KEY_TOGGLE_TRANSLATION", false);

        // Retrieve from preferences, and set in this Activity, the capture mode preference
        if (prefs.getBoolean("KEY_CONTINUOUS_PREVIEW", CaptureActivity.DEFAULT_TOGGLE_CONTINUOUS)) {
            isContinuousModeActive = true;
        } else {
            isContinuousModeActive = false;
        }

        // Retrieve from preferences, and set in this Activity, the page segmentation mode preference
        String[] pageSegmentationModes = getResources().getStringArray(R.array.pagesegmentationmodes);
        String pageSegmentationModeName = prefs.getString("KEY_PAGE_SEGMENTATION_MODE", pageSegmentationModes[0]);
        if (pageSegmentationModeName.equals(pageSegmentationModes[0])) {
            pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD;
        } else if (pageSegmentationModeName.equals(pageSegmentationModes[1])) {
            pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_AUTO;
        } else if (pageSegmentationModeName.equals(pageSegmentationModes[2])) {
            pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK;
        } else if (pageSegmentationModeName.equals(pageSegmentationModes[3])) {
            pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR;
        } else if (pageSegmentationModeName.equals(pageSegmentationModes[4])) {
            pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN;
        } else if (pageSegmentationModeName.equals(pageSegmentationModes[5])) {
            pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE;
        } else if (pageSegmentationModeName.equals(pageSegmentationModes[6])) {
            pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_WORD;
        } else if (pageSegmentationModeName.equals(pageSegmentationModes[7])) {
            pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT;
        } else if (pageSegmentationModeName.equals(pageSegmentationModes[8])) {
            pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT;
        }

        // Retrieve from preferences, and set in this Activity, the OCR engine mode
        String[] ocrEngineModes = getResources().getStringArray(R.array.ocrenginemodes);
        String ocrEngineModeName = prefs.getString("KEY_OCR_ENGINE_MODE", ocrEngineModes[0]);
        if (ocrEngineModeName.equals(ocrEngineModes[0])) {
            ocrEngineMode = TessBaseAPI.OEM_TESSERACT_ONLY;
        } else if (ocrEngineModeName.equals(ocrEngineModes[1])) {
            ocrEngineMode = TessBaseAPI.OEM_CUBE_ONLY;
        } else if (ocrEngineModeName.equals(ocrEngineModes[2])) {
            ocrEngineMode = TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED;
        }

        // Retrieve from preferences, and set in this Activity, the character blacklist and whitelist
        characterBlacklist = OcrCharacterHelper.getBlacklist(prefs, sourceLanguageCodeOcr);
        characterWhitelist = OcrCharacterHelper.getWhitelist(prefs, sourceLanguageCodeOcr);

        prefs.registerOnSharedPreferenceChangeListener(listener);

        beepManager.updatePrefs();
    }

    /**
     * Sets default values for preferences. To be called the first time this app is run.
     */
    private void setDefaultPreferences() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Continuous preview
        prefs.edit().putBoolean("KEY_CONTINUOUS_PREVIEW", CaptureActivity.DEFAULT_TOGGLE_CONTINUOUS).commit();

        // Recognition language
        prefs.edit().putString("KEY_SOURCE_LANGUAGE_PREFERENCE", CaptureActivity.DEFAULT_SOURCE_LANGUAGE_CODE).commit();

        // OCR Engine
        prefs.edit().putString("KEY_OCR_ENGINE_MODE", CaptureActivity.DEFAULT_OCR_ENGINE_MODE).commit();

        // Autofocus
        prefs.edit().putBoolean(KEY_AUTO_FOCUS, CaptureActivity.DEFAULT_TOGGLE_AUTO_FOCUS).commit();

        // Disable problematic focus modes
        prefs.edit().putBoolean("KEY_DISABLE_CONTINUOUS_FOCUS", CaptureActivity.DEFAULT_DISABLE_CONTINUOUS_FOCUS).commit();

        // Beep
        prefs.edit().putBoolean(KEY_PLAY_BEEP, CaptureActivity.DEFAULT_TOGGLE_BEEP).commit();

        // Character blacklist
        prefs.edit().putString("KEY_CHARACTER_BLACKLIST",
                OcrCharacterHelper.getDefaultBlacklist(CaptureActivity.DEFAULT_SOURCE_LANGUAGE_CODE)).commit();

        // Character whitelist
        prefs.edit().putString("KEY_CHARACTER_WHITELIST",
                OcrCharacterHelper.getDefaultWhitelist(CaptureActivity.DEFAULT_SOURCE_LANGUAGE_CODE)).commit();

        // Page segmentation mode
        prefs.edit().putString("KEY_PAGE_SEGMENTATION_MODE", CaptureActivity.DEFAULT_PAGE_SEGMENTATION_MODE).commit();

        // Reversed camera image
        prefs.edit().putBoolean(KEY_REVERSE_IMAGE, CaptureActivity.DEFAULT_TOGGLE_REVERSED_IMAGE).commit();

        // Light
        prefs.edit().putBoolean(KEY_TOGGLE_LIGHT, CaptureActivity.DEFAULT_TOGGLE_LIGHT).commit();
    }

    void displayProgressDialog() {
        // Set up the indeterminate progress dialog box
        indeterminateDialog = new ProgressDialog(this);
        indeterminateDialog.setTitle("Tunggu Sebentar");
        String ocrEngineModeName = getOcrEngineModeName();
        if (ocrEngineModeName.equals("Both")) {
            indeterminateDialog.setMessage("Menjalankan data menggunakan Tesseract...");
        } else {
            indeterminateDialog.setMessage("Menjalankan data menggunakan " + ocrEngineModeName + "...");
        }
        indeterminateDialog.setCancelable(false);
        indeterminateDialog.show();
    }

    ProgressDialog getProgressDialog() {
        return indeterminateDialog;
    }

    /**
     * Displays an error message dialog box to the user on the UI thread.
     *
     * @param title   The title for the dialog box
     * @param message The error message to be displayed
     */
    void showErrorMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setOnCancelListener(new FinishListener(this))
                .setPositiveButton("Done", new FinishListener(this))
                .show();
    }

}
