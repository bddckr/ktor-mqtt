package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.PublishResponse

public open class MqttException internal constructor(message: String? = null, cause: Throwable? = null) :
    RuntimeException(message, cause)

public class MalformedPacketException(message: String? = null) :
    MqttException(message)

public class ConnectionException(message: String? = null, cause: Throwable? = null) :
    MqttException(message = message, cause = cause)

public class PublishResponseException(publishResponse: PublishResponse) :
    MqttException(message = "Publish response error: $publishResponse")

public class TimeoutException(message: String) :
    MqttException(message)

public class TopicAliasException(message: String?) :
    MqttException(message)