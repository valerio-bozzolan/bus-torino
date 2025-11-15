package it.reyboz.bustorino.backend

enum class LivePositionsServiceStatus {
    OK,CONNECTING, NO_POSITIONS, ERROR_CONNECTION, ERROR_PARSING_RESPONSE, ERROR_NETWORK_RESPONSE
}