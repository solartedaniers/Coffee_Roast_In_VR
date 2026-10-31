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

## Cómo quedó la calibración

Tomé al experto como un lote de 6 kg: su hoja no dice cuánto pesaba el lote, y es el peso con el que su punto de retorno y su tiempo de tueste encajan mejor con el simulador. Con ese ancla ajusté el gas, la conducción y la sonda en `RoastConstants.js`:

| Constante | Valor | Para qué |
|---|---|---|
| `MAX_AIR_TEMP_C` y `AIR_TARGET_GAS_EXPONENT` | 239 °C y 0,105 | El aire llega a ~205 °C con 20 %, ~219 °C con 40 % y ~224 °C con 50 % |
| `HEAT_TRANSFER_COEFF_PER_SEC` | 0,0038 | El primer crack cae entre 8 y 12 min siguiendo la guía |
| `CHARGE_DIP_FLOOR_AIR_WEIGHT` | 0 | El grano entra a su propia temperatura; la sonda es la que baja en curva |
| `PROBE_RESPONSE_TIME_CONSTANT_SEC` | 79 s | La sonda no baja más rápido que la caída más fuerte del experto (2,2 °C/s) |
| `EXO_HEAT_RATE_C_PER_SEC` | 0,002 | La curva sigue subiendo después del crack sin pasarse de 212 °C |
| `FLAME_POWER_DEFAULT_PCT` | 50 % | El experto arranca con la llama al 50 % |

Con el gas que usó el experto en cada tueste y la descarga en un DTR del 20 %, esto es lo que marca el simulador:

| Lote | Punto de retorno | Primer crack | Temperatura final |
|---|---|---|---|
| 6 kg | 97,1–99,0 °C a 2:07–2:10 | 10:46–11:43 | 205,9–206,3 °C |
| 5,5 kg | 100,0–102,1 °C a 2:02–2:05 | 10:00–11:01 | 207,7–208,6 °C |
| 5 kg | 103,2–105,4 °C a 1:57–2:00 | 9:17–10:18 | 209,8–211,2 °C |

### El punto de retorno depende de la masa del lote

En el lote de 6 kg el retorno queda en 98–99 °C, como en el experto. En un lote más liviano el grano se calienta más rápido y la sonda toca fondo más arriba: hasta ~105 °C con 5 kg. No hay un solo ajuste que deje los tres lotes en 98–99 °C, así que el test pide 98,5 ± 3 °C en 6 kg y entre 94 y 106 °C en cualquier lote.

La hora del retorno también sale un poco antes. Con la misma regla (la sonda sube 0,5 °C sobre su mínimo), el simulador marca el retorno a las 2:07–2:10 en 6 kg y el experto a las 2:20 y 2:50: en promedio unos 20–30 s antes. Las lecturas del experto son enteras y van cada 5 s, así que la subida de 0,5 °C le aparece más tarde.

### La guía y la cobertura de granos

La guía de `es.json` es la estrategia del experto: 50 % hasta unos 185 °C, 40 % hasta el primer crack y 20 % después. Encima le pide al jugador mirar el Increm °T: si sube más de 16 °C por minuto, bajar a 40 % antes y dejarlo ahí; y solo si ya está en 40 % y sube menos de 10 °C por minuto, volver a 50 % hasta el crack. Así el jugador mueve el gas pocas veces, como un tostador real. El test simula a un jugador que sigue ese texto al pie de la letra.

Siguiendo esa guía, el grano de referencia (densidad 0,75 y humedad 10 %) cruza entre las 8:48 y las 11:32 en lotes de 5, 5,5 y 6 kg, con el umbral del crack en sus dos extremos.

Con granos aleatorios no todos caben en la ventana: la densidad sola mueve el primer crack unos 3 min (de 8:53 a 11:49 en un lote de 5,5 kg), y el peso del lote, el umbral del crack y la humedad suman más. En una muestra de 3000 granos sorteados igual que en una sesión real:

| Primer crack | Granos |
|---|---|
| Entre 8 y 12 min | 93,8 % |
| Antes de 8 min | 0,3 % |
| Entre 12 y 13 min | 5,5 % |
| Después de 13 min | 0,4 % |

El rango completo va de 7:46 a 13:42 y la temperatura final, de 202,0 a 210,3 °C. El test usa 500 granos con semilla fija y pide al menos el 90 %. Para los granos lentos hay una alerta a los 9 min (`ROAST_RUNNING_SLOW` en el catálogo de la IA) que le pide al jugador subir el gas mientras todavía alcanza a corregir.

### El puntaje del tueste perfecto

Antes el puntaje restaba desde un punto exacto: 200,5 °C de temperatura final y 13 min de duración. Con la física calibrada eso castigaba al propio experto, que perdía entre 11 y 34 puntos solo por temperatura y hasta 7 por duración. Ahora uso franjas de diseño sin castigo:

| Franja | Valor | De dónde sale |
|---|---|---|
| Temperatura final (`IDEAL_FINAL_TEMP_MIN_C` a `IDEAL_FINAL_TEMP_MAX_C`) | 205 a 212 °C | La del tueste medio con DTR del 20 % siguiendo al experto en lotes de 5 a 6 kg |
| Duración total (`IDEAL_TOTAL_ROAST_MIN_MINUTES` a `IDEAL_TOTAL_ROAST_MAX_MINUTES`) | 10 a 15 min | La ventana del primer crack de 8 a 12 min con DTR del 20 % |

Fuera de la franja se resta con los mismos pesos de antes: 2,5 puntos por °C y 4 por minuto de distancia al borde, multiplicados por el nivel. Con esto los seis tuestes del experto y el grano de referencia con la guía sacan 100 en los tres niveles, y en 1000 granos al azar con la guía la mediana es 100 y el peor saca 91 en INTERMEDIATE. Entre tuestes buenos la diferencia la ponen el DTR y la nota de la rúbrica. Un test comprueba que el gas del experto no pierde puntos por temperatura ni por duración.

### Lo que medí y no apliqué

Probé que la evaporación empiece a 100 °C en vez de 120 °C. Contra el experto empeora: el error medio de la sonda después del retorno sube de ~15 a ~16 °C en el lote de 6 kg y de ~26 a ~28 °C en el de 5 kg, y la temperatura final del lote de 5 kg llega a 212,0 °C. Por eso la dejé en 120 °C.

