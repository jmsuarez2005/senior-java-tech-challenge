# API de productos con precios históricos

Solución a la prueba técnica de [SPEC.md](SPEC.md). Un producto puede tener muchos precios a lo largo del tiempo, pero
solo uno vigente por día y moneda. Java 21, Quarkus 3 en su modelo reactivo, MongoDB, Redis con una caché local
delante y OAuth 2.0 con Keycloak.

## Cómo ejecutarlo

Solo hace falta Docker:

```bash
docker compose up --build
```

Levanta la API, MongoDB, Redis y Keycloak, carga un catálogo de ejemplo y, cuando la API está lista, lanza la prueba
de rendimiento con k6. La API responde en `http://localhost:8080` unos 5 s después de que MongoDB y Redis estén
listos; no espera a Keycloak, que solo hace falta para modificar o borrar precios.

Solo la API, sin Keycloak ni prueba de carga:

```bash
docker compose up --build app
```

| URL | Qué es |
|---|---|
| `http://localhost:8080/swagger-ui` | Swagger UI: documentación interactiva de todos los endpoints |
| `http://localhost:8080/actuator/health` | Estado de la API y de MongoDB |
| `http://localhost:8080/q/metrics` | Métricas Prometheus |
| `http://localhost:8180` | Keycloak, el servidor OAuth 2.0 (admin / admin) |

Compilar y pasar los tests (Java 21 y Docker, que levanta MongoDB y Redis para los tests de integración):

```bash
sh gradlew build
```

El wrapper venía en el enunciado sin permiso de ejecución, de ahí `sh gradlew`. El informe de cobertura queda en
`build/jacoco-report/index.html`.

**Datos de ejemplo.** Cada arranque carga 8 productos con ids fijos desde [seed/catalog.json](seed/catalog.json),
cada uno pensado para un caso (el ejemplo del enunciado, una rebaja entre dos periodos, varias monedas, doce precios
para paginar, un producto sin precios...):

```bash
curl -s 'localhost:8080/products/665000000000000000000001/prices?date=2024-04-15'   # {"value":99.99}
```

## Endpoints

Los cuatro endpoints del enunciado se mantienen **exactamente como están definidos** (rutas, verbos y cuerpos),
porque las pruebas automáticas los usan:

| Método | Ruta | Respuesta |
|---|---|---|
| `POST` | `/products` | `201`, `Location` a `/products/{id}`, cuerpo `{id, name, description}` |
| `POST` | `/products/{id}/prices` | `201`, el precio creado |
| `GET` | `/products/{id}/prices?date=2024-04-15` | `200`, `{"value": 99.99}` |
| `GET` | `/products/{id}/prices` | `200`, `{name, description, prices: [...]}` ordenado por `initDate` |

Sobre estos endpoints, el enunciado dice:

> Los endpoints anteriores se utilizarán en las pruebas automáticas.
> Sin embargo, **si consideras que alguno puede mejorarse para alinearse mejor con la semántica REST**, puedes hacerlo
> libremente, justificándolo en el README de tu proyecto.

Las mejoras se han añadido junto a los originales, nunca en su lugar, en una versión `/v1` de la misma API. Las dos
comparten implementación y responden igual:

- **`/v1`** para poder publicar un cambio incompatible como `/v2` sin romper a nadie.
- **`GET /v1/products/{id}/price?date=`**: el precio vigente es un único recurso, así que la ruta va en singular.
  `/prices?date=` se mantiene porque es la forma del enunciado.
- **`GET /v1/products/{id}`** devuelve el producto sin sus precios.
- **`PUT` y `DELETE /v1/products/{id}/prices/{currency}/{initDate}`** modifican o borran un precio. Un precio no
  tiene id propio: lo identifican su moneda y su `initDate`, únicos porque los periodos no se solapan. Requieren un
  token OAuth 2.0 con el scope `products:write`.
- **`ETag` / `If-Match`** en esas dos operaciones: si el producto cambió desde que se leyó, la respuesta es `412` y
  no se escribe nada. Es opcional.
- **Monedas**: `currency` opcional (ISO 4217, `EUR` por defecto) al crear y al consultar.
- **Historial** con filtro (`from`, `to`), orden (`sort`) y paginación (`page`, `size`); el total y las páginas
  vecinas van en las cabeceras `X-Total-Count` y `Link`, así que el cuerpo no cambia de forma.

Los errores siguen RFC 9457 (`application/problem+json`): `400` datos no válidos, con cada campo erróneo en
`errors`; `401` o `403` sin token o sin permiso; `404` producto o precio inexistente; `409` solapamiento; `412`
versión desactualizada; `415` cuerpo que no es JSON.

## Supuestos

- **El periodo es un intervalo cerrado**: `initDate` y `endDate` están incluidos, que es como se leen los ejemplos
  del enunciado. Un precio que acaba el `2024-06-30` sigue vigente ese día, así que otro que empiece ese mismo día
  se solapa.
- `endDate` puede ser `null`: vigente hasta nuevo aviso. Si existe, `initDate` debe ser estrictamente anterior.
- `value` debe ser positivo y no puede tener más decimales de los que admite su moneda (`9.999` EUR se rechaza, no
  se redondea). El límite es de 18 dígitos, lo que también impide valores como `1e10000000` diseñados para consumir
  CPU.
- El solapamiento se comprueba **por moneda**: cada moneda tiene su propia línea de tiempo. Sin moneda se usa `EUR`,
  así que con los endpoints del enunciado la regla es exactamente la pedida.
- Los cuatro endpoints del enunciado son públicos para que las pruebas automáticas puedan llamarlos;
  `PRODUCTS_AUTH_ALL=true` exige token en todos.

## Decisiones técnicas

| Pieza | Elección | Por qué |
|---|---|---|
| Lenguaje y framework | Java 21, Quarkus 3 | El arranque más rápido y el menor consumo de memoria de las opciones medidas |
| Modelo de ejecución | Reactivo (Mutiny sobre Vert.x) | Con Redis superó a los hilos virtuales en rendimiento y latencia de cola |
| Base de datos | MongoDB, un documento por producto con sus precios | Toda lectura es una sola búsqueda por id, y la regla de no solapamiento es una única escritura atómica |
| No solapamiento | `findOneAndUpdate` con el filtro de solapamiento | MongoDB comprueba y escribe a la vez: dos peticiones simultáneas no pueden pasar las dos. Un test lanza 50 a la vez y solo entra una |
| Caché | Caché local (Caffeine, 5 s) delante de Redis (1 min) | La mejor configuración medida. Redis descarga a MongoDB, que solo tiene media CPU; la caché local evita la red en los productos más leídos |
| Arquitectura | Hexagonal con DDD | El dominio no depende de ningún framework; ArchUnit lo comprueba en cada compilación. El cambio de Spring a Quarkus solo tocó los adaptadores |
| Dinero | `long` en la unidad mínima de cada moneda | Sin errores de coma flotante; respeta los decimales de cada moneda (JPY 0, KWD 3) |
| Seguridad | OAuth 2.0 client credentials, Keycloak, SmallRye JWT | El estándar entre microservicios; la API valida el token en local, sin llamar a Keycloak en cada petición |

**El stack se eligió midiendo.** Se probaron seis configuraciones con la misma carga (450 peticiones por segundo),
cada una en su propia rama:

| Rama | Configuración | p95 | p99 | CPU por petición | Arranque |
|---|---|---|---|---|---|
| `experiment/01-spring-webflux` | Spring Boot 4, WebFlux + Redis | 151 ms | 316 ms | 2.971 µs | 10,5 s |
| `experiment/02-spring-mvc-virtual-threads` | Spring Boot 4, MVC con hilos virtuales + Redis | 735 ms | 1.126 ms | 3.088 µs | 11,4 s |
| `experiment/03-quarkus-virtual-threads` | Quarkus, hilos virtuales + Redis | 54 ms | 132 ms | 2.190 µs | 4,4 s |
| `experiment/04-quarkus-reactive` | Quarkus reactivo + Redis | 57 ms | 208 ms | 1.887 µs | 4,4 s |
| `experiment/05-review-fixes` | + correcciones de la revisión técnica | 65 ms | 159 ms | 2.007 µs | 4,3 s |
| `experiment/06-near-cache` | **+ caché local delante de Redis (ganadora)** | **39 ms** | **92 ms** | **1.847 µs** | **4,3 s** |
| `main` | la ganadora + OAuth, observabilidad y endpoints nuevos | 49 ms | 152 ms | 1.902 µs | 5,1 s |

Ninguna configuración devolvió errores. La caché local tiene un coste: con varias instancias, cada una guarda su
propia copia y puede servir un precio cambiado en otra durante hasta 5 s. Para este ejercicio, con una sola
instancia, es la mejor opción; con varias se desactiva con `PRODUCTS_LOCAL_CACHE_ENABLED=false` y queda solo Redis.
Las implicaciones y alternativas están en [docs/DESIGN.md](docs/DESIGN.md#local-cache-in-distributed-microservices).

## Prueba de rendimiento

`docker compose up --build` la lanza sola cuando la API está lista. El script, [perf/load-test.js](perf/load-test.js),
usa los endpoints del enunciado:

1. crea 1.000 productos con 5 precios cada uno,
2. durante 2 minutos sube a 200 usuarios concurrentes y los mantiene,
3. reparte las peticiones en 70% precio vigente, 20% historial y 10% precios nuevos,
4. falla si más del 1% de las peticiones da error,
5. guarda el informe en `perf/results/summary-<nombre>.json`.

Para comparar configuraciones con la misma carga, `RATE` fija las peticiones por segundo y además exige un p95
menor de 50 ms:

```bash
RUN=ritmo-fijo RATE=450 docker compose up --build
```

Los límites de CPU y memoria de `app` y `benchmark` son los del enunciado, sin tocar. Los contenedores auxiliares
(MongoDB, Redis, Keycloak y la carga del catálogo) no pasan de 0,5 CPU y 1 GB cada uno. Todas las mediciones están en
[perf/RESULTS.md](perf/RESULTS.md).

## Tests

- **133 tests** unitarios, de integración y de arquitectura (`sh gradlew build`), con un 96% de cobertura de
  instrucciones.
- **66 casos funcionales** en Karate que prueban cada endpoint contra la aplicación levantada, con tokens reales de
  Keycloak. Los casos y sus escenarios están en [docs/TEST-CASES.md](docs/TEST-CASES.md):

  ```bash
  docker compose up -d --build app keycloak
  sh gradlew functionalTest
  ```

- La prueba de rendimiento con k6.

Todo se ejecuta en cada push con GitHub Actions.

## Desafíos opcionales

| Desafío | Cómo |
|---|---|
| Prueba de rendimiento automatizada | k6 dentro de `docker compose`, con informe por ejecución |
| Varias monedas por precio | `currency` opcional; cada moneda tiene su propia línea de tiempo |
| Actualizar o eliminar precios | `PUT` y `DELETE` en `/v1`, con comprobación atómica de solapamiento y `If-Match` |
| Autenticación | OAuth 2.0 client credentials con Keycloak; scope `products:write` |
| Documentación Swagger | Swagger UI con ejemplos, errores y login OAuth (cliente `products-writer`, secreto `products-writer-secret`) |
| Datos de prueba automáticos | Catálogo de ejemplo cargado en cada arranque; `scripts/seed.sh` para datos masivos |
| Paginación, orden y filtros en el historial | `from`, `to`, `sort`, `page` y `size` |

## Más detalle

- [docs/DESIGN.md](docs/DESIGN.md): diseño completo, seguridad, observabilidad, cachés y cómo se eligió el stack.
- [docs/DECISIONS.md](docs/DECISIONS.md): cada decisión técnica en una línea.
- [docs/TEST-CASES.md](docs/TEST-CASES.md): estrategia y casos de prueba.
- [perf/RESULTS.md](perf/RESULTS.md): todas las mediciones de rendimiento.
