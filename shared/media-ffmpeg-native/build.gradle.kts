description = "In-process FFmpeg video decoder backed by JavaCPP"

val javaCppVersion = "1.5.14"
val ffmpegVersion = "8.1.2-1.5.14"

dependencies {
    api(project(":shared:media-api"))
    implementation("org.bytedeco:javacpp:$javaCppVersion")
    implementation("org.bytedeco:ffmpeg:$ffmpegVersion")
    implementation("org.bytedeco:javacv:$javaCppVersion") {
        isTransitive = false
    }
}
