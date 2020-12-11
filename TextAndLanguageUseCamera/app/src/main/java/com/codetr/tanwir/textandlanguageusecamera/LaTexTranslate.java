package com.codetr.tanwir.textandlanguageusecamera;

public class LaTexTranslate {

    /**
     * Function to translate the current mathematical expression and its result, to the LaTex
     * language
     */
    public static String translateEquation(String equation, String result){

        // Homogenize the read variable
        equation = equation.replaceAll("X", "x");
        // Puts the opening of the LaTex environment and ident the next line
        String translated = "\\begin{align*} \n    ";

        // Translation of the equation recognized by the OCR algorithm
        for (int i=0; i < equation.length(); i++){

            String temp = "";
            
            // Read the next value, looking for a multiplication or division
            if ( i < (equation.length() - 2))
                temp = equation.substring(i + 1, i + 2);

            // Case for the division symbol
            if ( temp.contains("/") ){

                translated = translated + "\\frac{" + equation.substring(i, i+1) + "}{" +
                        equation.substring(i+2, i+3) + "}";

                i += 2;
            }
            else {  // Case for the multiplication symbol

                if ( temp.contains("*") ){

                    translated =  translated + equation.substring(i, i+1) + "\\cdot ";
                    i++;
                }
                else {

                    translated =  translated + equation.substring(i, i+1);
                }
            }
        }

        // Build the final result for the equations
        if( equation.contains("=") ){

            if ( result.equals("Soluciones infinitas") )
                result = "soluciones \\; infinitas";

            if ( result.equals("Sin solucion") )
                result = "sin \\; solucion";


            translated = translated + " \\\\ \n    x = " + result;

        }
        else {  // Build the final result for the expressions

            translated = translated + " = " + result;
        }
        // Puts the closing of the LaTex environment
        translated = translated + "\n \\end{align*}";
        return translated;
    }
}
