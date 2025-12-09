# PowerShell 脚本：下载 Spring AI 依赖
# 使用方法：在有外网的环境中运行此脚本，然后将下载的文件传输到内网环境

$BaseUrl = "https://repo.spring.io/milestone"
$GroupId = "org.springframework.ai"
$ArtifactId = "spring-ai-openai-spring-boot-starter"
$Version = "1.0.0-M4"

$DownloadDir = "spring-ai-dependencies"
New-Item -ItemType Directory -Force -Path $DownloadDir | Out-Null

Write-Host "开始下载 Spring AI 依赖..." -ForegroundColor Green

# 构建路径（将点替换为斜杠）
$PathPart = $GroupId -replace '\.', '/'
$BasePath = "$BaseUrl/$PathPart/$ArtifactId/$Version"

# 下载 POM 文件
$PomUrl = "$BasePath/$ArtifactId-$Version.pom"
$PomFile = "$DownloadDir\$ArtifactId-$Version.pom"
Write-Host "下载 POM: $PomUrl" -ForegroundColor Yellow
Invoke-WebRequest -Uri $PomUrl -OutFile $PomFile

# 下载 JAR 文件
$JarUrl = "$BasePath/$ArtifactId-$Version.jar"
$JarFile = "$DownloadDir\$ArtifactId-$Version.jar"
Write-Host "下载 JAR: $JarUrl" -ForegroundColor Yellow
Invoke-WebRequest -Uri $JarUrl -OutFile $JarFile

Write-Host "`n下载完成！文件保存在 $DownloadDir 目录" -ForegroundColor Green
Write-Host ""
Write-Host "接下来需要：" -ForegroundColor Cyan
Write-Host "1. 下载所有传递依赖（使用 Gradle 或 Maven 的依赖树功能）"
Write-Host "2. 将文件安装到本地 Maven 仓库或内网制品库"
Write-Host ""
Write-Host "安装到本地 Maven 仓库的命令：" -ForegroundColor Cyan
Write-Host "mvn install:install-file \"
Write-Host "  -Dfile=$JarFile \"
Write-Host "  -DgroupId=$GroupId \"
Write-Host "  -DartifactId=$ArtifactId \"
Write-Host "  -Dversion=$Version \"
Write-Host "  -Dpackaging=jar \"
Write-Host "  -DpomFile=$PomFile"

