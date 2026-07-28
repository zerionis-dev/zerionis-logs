# Zerionis Log

Librería de logging estructurado para Java. `com.zerionis:zerionis-log`, versión 1.2.0.
Cuatro módulos Maven: `zerionis-log-core` (lógica pura, sin Spring) y tres starters,
`zerionis-log-spring-boot2`, `zerionis-log-spring-boot3` y `zerionis-log-spring-boot4`.

**Es el único artefacto del ecosistema Zerionis que se publica a Maven Central y que
consume alguien de fuera.** Eso cambia el cálculo de riesgo respecto a los demás repos:
Krono Cloud, Pulse o Maya se despliegan enteros y un error se corrige con el siguiente
deploy, pero una versión publicada aquí no se puede retirar. De ahí que los gates de este
repo cubran también el camino de publicación y no solo el de la rama.

## Nivel de gate: 1

Medido el 2026-07-28 sumando los reportes de surefire módulo por módulo, no contando
archivos: **196 pruebas en 63 clases** (120 en 42 clases de `zerionis-log-core`, 26 en 7 de
`zerionis-log-spring-boot2`, 26 en 7 de `zerionis-log-spring-boot3`, 24 en 7 de
`zerionis-log-spring-boot4`).

Es nivel 1 y no 2 porque de lo que pide el nivel 2 hoy no corre nada: no hay ArchUnit, ni
Error Prone con NullAway, ni umbral de cobertura sobre líneas nuevas, ni SpotBugs con
find-sec-bugs. Las otras dos piezas del nivel 2, lint de migraciones y presupuesto de
bundle, no aplican: esta librería no tiene base de datos ni frontend. Declarar nivel 2 sin
esas piezas sería exactamente el error que el nivel sirve para evitar, porque un nivel
declarado que no se cumple hace que nadie revise.

La tabla de qué corre en cada nivel está en el CLAUDE.md global. La implementación de
referencia del nivel 3 completo es `C:\Workspace\zerionis-cimiento` (`pom.xml` y
`.github/workflows/ci.yml`); el porqué de cada gate, `C:\Workspace\docs\sistema-de-trabajo.html`
secciones 9 a 14.

## Qué rompe el build hoy (verificado el 2026-07-28)

- **gitleaks sobre todo el histórico**, en los dos caminos: job `secretos` de
  `.github/workflows/ci.yml` y job `secretos` de `.github/workflows/publish.yml`, este
  último en el `needs:` de `publish`. Con `fetch-depth: 0`, porque sin el histórico
  completo solo vería el último commit y un secreto que se commiteó y se borró después
  sigue estando filtrado. Corrido local el 2026-07-28: reporta 39 commits escaneados,
  471 KB, `no leaks found`, exit 0. El repo tiene 44 commits, de los cuales 6 son merges
  cuyo diff `git log -p` no emite, así que el universo escaneable son 38 y el número que
  reporta gitleaks cuadra con eso.
- **`mvn -B verify`** (`ci.yml`, job `build`): las 196 pruebas más el
  `maven-enforcer-plugin` con `dependencyConvergence`, `requireMavenVersion` 3.6,
  `requireJavaVersion` 11 y `bannedDependencies` sobre `log4j-core` menor a 2.17.1.
- **`mvn -B deploy`** (`publish.yml`), que corre el reactor completo con pruebas antes de
  publicar.
- **Trivy sobre las dependencias** (`ci.yml`, job `dependencias`), severidad
  `CRITICAL,HIGH`, `ignore-unfixed`, `exit-code: 1`.
- **`--strict-checksums`** en `.mvn/maven.config`: un artefacto descargado cuyo checksum no
  cuadra rompe la resolución en vez de continuar con una advertencia.
- **SBOM CycloneDX**: `cyclonedx-maven-plugin` corre en `verify` y en `deploy`, y el
  `bom.json` viaja adjunto al artefacto publicado como
  `zerionis-log-spring-boot4-<version>-cyclonedx.json`.

## Lo que se arregló el 2026-07-28, y por qué importa

Los cuatro problemas eran de la misma familia: gates declarados que no se ejecutaban. Un
gate neutralizado es peor que uno ausente, porque produce confianza que nadie ganó.

1. **`-Dmaven.test.skip=true` en el paso de publicación.** Dejaba salir a Maven Central sin
   correr ninguna de las 196 pruebas, en el único artefacto que consume alguien de fuera.
   La bandera es la agresiva: no solo se salta la ejecución, se salta la compilación de las
   pruebas. La puso el commit `c031822` (2026-04-13) para esquivar una falla de compilación
   de tests que ya no ocurre. Se comprobó reproduciendo localmente la ruta completa de
   publicación (`versions:set` a una versión de prueba, preinstall de core, reactor
   completo con pruebas): `BUILD SUCCESS` en los cinco entries del reactor. La bandera se
   quitó.
2. **`-Dcyclonedx.skip=true` en los dos caminos.** `README.md:208` promete generación de
   SBOM como característica de seguridad del artefacto, y no se generaba en ninguno de los
   dos. Se quitó del build completo y del deploy, y se dejó solo en los pasos
   `Pre-install core`, que son preinstalaciones desechables de las que nadie consume el
   SBOM.
3. **`ossindex-maven-plugin` fallaba abierto.** Su comentario en el `pom.xml` decía que era
   gratis y sin API key. Al desactivar su skip y correrlo, la API de OSS Index responde
   **HTTP 401 Unauthorized** y el plugin emite solo `[WARNING]`, así que el build terminaba
   verde habiendo escaneado cero dependencias. Se eliminó del `pom.xml` y su función la
   toma Trivy en CI, que sí rompe el build. Un gate que pasa cuando no puede leer no es un
   gate.
4. **`publish.yml` no tenía gate de secretos.** Una etiqueta se puede poner sobre cualquier
   commit, incluido uno que nunca pasó por el CI de la rama, así que el camino de
   publicación necesita su propia verificación y no puede confiar en la del push.

## El pin de Jackson 2.x

El `pom.xml` raíz importa `com.fasterxml.jackson:jackson-bom` en la versión
`${jackson2-fixed.version}` (2.21.4) **antes** del BOM de Spring Boot 4, y ese orden es el
mecanismo: entre varios `import` dentro del mismo bloque `dependencyManagement`, gana el
primero.

El BOM de Boot 4 gestiona Jackson 2.x en 2.21.2, que arrastra `CVE-2026-54512` y
`CVE-2026-54513` (ejecución de código arbitrario en `jackson-databind`) más
`GHSA-r7wm-3cxj-wff9` en `jackson-core`. Los tres son HIGH y sí llegan a quien consume el
artefacto, porque `zerionis-log-spring-boot4` declara Jackson 2.x en scope `compile` a
propósito: Boot 4 trae Jackson 3.x bajo el namespace `tools.jackson.*` y
`zerionis-log-core` está compilado contra `com.fasterxml.jackson.*`. El razonamiento
completo está en `zerionis-log-spring-boot4/pom.xml:51-67`. Los detectó Trivy el
2026-07-28.

**La primera hipótesis, pasar Jackson a scope `provided` como en boot2 y boot3, era
incorrecta y se descartó** justamente por lo anterior: en boot4 el consumidor no aporta esa
dependencia, la necesita del starter.

Solo `zerionis-log-spring-boot4` se veía afectado. Los módulos boot2 y boot3 importan su
propio BOM de Spring Boot dentro de su propio `dependencyManagement`, que tiene precedencia
sobre lo heredado del padre, y por eso siguen en Jackson 2.13.5 y 2.15.4 respectivamente,
ambos en scope `provided`. Verificado con `dependency:tree` en los tres módulos después del
pin, y con la suite completa en `BUILD SUCCESS`.

## Trivy necesita el repositorio local de Maven poblado

El job `dependencias` corre `mvn -B dependency:go-offline` antes de Trivy y le pasa
`TRIVY_OFFLINE_SCAN=true`. No es adorno: Trivy lee los `pom.xml` y resuelve las versiones
efectivas, y con el repositorio local vacío sale a buscar los POM padre a
`repo.maven.apache.org`, donde recibe `429 Too Many Requests` con `Retry-After: 1800` y
aborta el escaneo con FATAL. Reproducido localmente el 2026-07-28. Un gate que se pone rojo
por no poder leer normaliza el rojo igual de rápido que uno que falla abierto normaliza el
verde.

## Publicar es irreversible

**Empujar una etiqueta `v*` dispara `.github/workflows/publish.yml` y publica a Maven
Central.** Una versión publicada no se puede retirar ni sobrescribir. No crear ni empujar
etiquetas sin orden explícita de Gabo, igual que cualquier otro deploy del ecosistema.

Este repo es el único donde el versionado **sí es semver**, porque aquí sí existe alguien
que importa el código y a quien la promesa de compatibilidad le sirve. El formato
`<fecha UTC>-<contador>` del resto del ecosistema no aplica.

## Qué falta para nivel 2

En orden de lo que más rinde primero:

1. **Umbral de cobertura sobre líneas nuevas.** Es lo que más aporta con 196 pruebas ya
   escritas: no exige subir un número global, solo impide que entre código nuevo sin
   prueba.
2. **Error Prone con NullAway en compilación.** Barato y encuentra defectos reales en una
   librería que corre dentro del proceso de quien la consume.
3. **SpotBugs con find-sec-bugs.**
4. **ArchUnit** para congelar la frontera que ya existe de hecho: `zerionis-log-core` no
   debe conocer nada de Spring, y ningún starter debe depender de otro starter.
