package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.annotations.Expose
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.scicomp.v5.VersionedN5Reader
import org.janelia.scicomp.v5.VersionedN5Writer
import java.io.IOException
import java.net.URI

data class N5VersionedMeta(
    @field:Expose private val n5: String,
    @field:Expose override val dataset: String,
) : N5Meta {

    @get:Throws(IOException::class)
    override val writer: VersionedN5Writer? by lazy { println(n5)
        n5Factory.openVersionedWriter(n5) }

    @get:Throws(IOException::class)
    override val reader: N5Reader = writer!!

    @Throws(ReflectionException::class)
    constructor(reader: VersionedN5Reader, dataset: String) : this(fromReader(reader), dataset)

    fun basePath() = n5

    companion object {
        @Throws(ReflectionException::class)
        private fun fromReader(reader: VersionedN5Reader): String {
                return reader.versionedUrl
        }
    }

}
