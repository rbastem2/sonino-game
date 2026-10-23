# Regras do SONINO
-keep class com.rbastem.sonino.** { *; }
-keep class androidx.core.splashscreen.** { *; }

# Remover logs de debug (Item 9 da tabela)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
