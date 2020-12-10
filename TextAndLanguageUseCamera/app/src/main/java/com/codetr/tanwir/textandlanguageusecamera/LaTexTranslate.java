package com.codetr.tanwir.textandlanguageusecamera;

public class LaTexTranslate {

    /**
     * Function to translate the current mathematical expression and its result, to the LaTex
     * language
     */
    public static String translateEquation(String equation, String result){

        String translated = "\\begin{align*} \n    ";

        for (int i=0; i < equation.length(); i++){

            String temp = "";
            
            if ( i < (equation.length() - 2))
                temp = equation.substring(i + 1, i + 2);


            if ( temp.contains("/") ){

                translated = translated + "\\frac{" + equation.substring(i, i+1) + "}{" +
                        equation.substring(i+2, i+3) + "}";

                i += 2;
            }
            else {

                if ( temp.contains("*") ){

                    translated =  translated + equation.substring(i, i+1) + "\\cdot ";
                    i++;
                }
                else {

                    translated =  translated + equation.substring(i, i+1);
                }
            }
        }

        if( equation.contains("=") ){

            if ( result.equals("Soluciones infinitas") )
                result = "soluciones \\; infinitas";

            if ( result.equals("Sin solucion") )
                result = "sin \\; solucion";


            translated = translated + " \\\\ \n    x = " + result;

        }
        else {

            translated = translated + " = " + result;
        }

        translated = translated + "\n \\end{align*}";
        return translated;
    }
}
