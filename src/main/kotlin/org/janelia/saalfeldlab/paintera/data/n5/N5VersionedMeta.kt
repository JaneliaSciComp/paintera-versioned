package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.annotations.Expose
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.scicomp.v5.fs.V5FSReader
import org.janelia.scicomp.v5.fs.V5FSWriter
import org.janelia.scicomp.v5.lib.uri.V5FSURL
import java.io.IOException

data class N5VersionedMeta(
    @field:Expose private val n5: String,
    @field:Expose override val dataset: String,
) : N5Meta {

    @get:Throws(IOException::class)
    override val writer: V5FSWriter? by lazy { println(n5)
        n5Factory.openVersionedWriter(n5) }

    @get:Throws(IOException::class)
    override val reader: N5Reader = writer!!

    @Throws(ReflectionException::class)
    constructor(reader: V5FSReader, dataset: String) : this(fromReader(reader), dataset)

    fun basePath(): String = V5FSURL(n5).keyValueStorePath

    companion object {
        @Throws(ReflectionException::class)
        private fun fromReader(reader: V5FSReader): String {
                return reader.versionedUrl
        }
    }

}
