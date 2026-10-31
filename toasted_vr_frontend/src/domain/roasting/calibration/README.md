# Datos del tostador experto

`expert-roasts.csv` tiene dos tuestes completos que hizo un tostador experto en la misma clase de máquina que modelo en el simulador: tostadora a gas de 5 a 6 kg, con el control de temperatura en 200 °C. Los uso como referencia para calibrar la forma de la curva y la respuesta al gas, y para comparar la curva simulada con la real en `ExpertCurveCalibration.test.js`.

## Columnas

| Columna | Qué es |
|---|---|
| `roast` | Número del tueste (1 o 2) |
| `seconds` | Segundos desde la carga del café |
| `probe_c` | Lo que marca la sonda metida en la masa de grano (°C) |
| `flame_pct` | Potencia de la llama (%) |
| `setpoint_c` | Temperatura de control de la máquina (°C), siempre 200 |
| `display_rate_c_per_min` | El incremento por minuto que muestra la máquina. Durante la caída lo muestra sin signo, por eso sale positivo aunque la temperatura baje |

## Cómo los convertí

Tomé la hoja `Hoja1` de `grafica_de_tostion.xlsx` sin cambiar ningún valor. En la hoja el tiempo está en minutos.segundos: `2.45` es 2 min 45 s, así que lo pasé a 165 s. Cada tueste empieza en 5 s, que es cuando se cargó el café con la máquina caliente por el tueste anterior (185 °C en el primero y 193 °C en el segundo). Dejé fuera el tercer tueste porque en la hoja solo quedó su primera fila.

Las muestras van cada 5 s, con dos huecos en el primer tueste: de 3:00 a 4:00 y de 7:00 a 7:20. La última fila de cada tueste es la descarga: ahí el gas vuelve al 50 % para el siguiente lote.

## Qué copio y qué no

Del experto copio la forma de la curva, los tiempos y la respuesta al gas: la sonda baja en curva durante unos 2 min hasta 98–99 °C (el punto de retorno), luego sube pareja a unos 9–13 °C/min, cada vez más lento, y el tueste dura unos 13 min. No copio las temperaturas del final (196–200 °C): su sonda lee más bajo, y las reglas del simulador usan los rangos de la industria (primer crack 196–205 °C, aviso de quemado 215 °C y techo 220 °C).
