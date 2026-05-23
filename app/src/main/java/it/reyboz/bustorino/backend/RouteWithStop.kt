package it.reyboz.bustorino.backend

data class RouteWithStop(
    val stop: Stop,
    val route: Route,
) {
    val id by lazy {
        "stop${stop.ID}route${route.displayCode}"
    }
}
