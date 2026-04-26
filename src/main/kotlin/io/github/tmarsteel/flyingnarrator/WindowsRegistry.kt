package io.github.tmarsteel.flyingnarrator

object WindowsRegistry {
    fun query(key: String, value: String?): List<KeyData> {
        val command = mutableListOf(
            "reg",
            "QUERY",
            key,
        )
        if (value != null) {
            command += listOf(
                "/v",
                value,
            )
        }
        val regProcess = ProcessBuilder(command).start()

        val result = regProcess.waitFor()

        if (result != 0) {
            return emptyList()
        }

        val data = mutableListOf<KeyData>()
        val lines = regProcess.inputReader(Charsets.UTF_8).readLines().drop(1)
        var index = 0
        while (index < lines.size) {
            val keyName = lines[index++]
            if (keyName.isBlank()) {
                continue
            }

            val values = mutableMapOf<String, Value>()
            while (index < lines.size && lines[index].startsWith("    ")) {
                val (valueName, valueType, valueAsString) = lines[index++].trimStart().split("    ", limit = 3)
                values[valueName] = when (valueType) {
                    "REG_SZ" -> RegSz(valueAsString)
                    "REG_EXPAND_SZ" -> RegExpandSz(valueAsString)
                    "REG_BINARY" -> RegBinary(valueAsString.hexToByteArray(HexFormat.UpperCase))
                    "REG_DWORD" -> RegDWord(valueAsString.removePrefix("0x").hexToInt(HexFormat.UpperCase))
                    "REG_MULTI_SZ" -> error("RegMultiSz is not supported")
                    else -> RegNone
                }
            }

            data.add(KeyData(keyName, values))
        }

        return data
    }

    data class KeyData(
        val name: String,
        val values: Map<String, Value>,
    )

    sealed interface Value
    data class RegSz(val value: String): Value
    data class RegExpandSz(val value: String): Value
    data class RegBinary(val value: ByteArray): Value
    data class RegDWord(val value: Int): Value
    data object RegNone: Value
}