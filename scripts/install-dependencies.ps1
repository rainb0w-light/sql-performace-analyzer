# PowerShell 脚本：批量安装 Spring AI 依赖到本地 Maven 仓库
# 使用方法：在包含下载的依赖文件的目录中运行此脚本

param(
    [string]$DependenciesDir = "spring-ai-dependencies",
    [string]$GroupId = "org.springframework.ai"
)

Write-Host "开始安装依赖到本地 Maven 仓库..." -ForegroundColor Green
Write-Host "依赖目录: $DependenciesDir" -ForegroundColor Yellow
Write-Host "Group ID: $GroupId" -ForegroundColor Yellow
Write-Host ""

if (-not (Test-Path $DependenciesDir)) {
    Write-Host "错误: 目录 $DependenciesDir 不存在！" -ForegroundColor Red
    exit 1
}

$jarFiles = Get-ChildItem -Path $DependenciesDir -Recurse -Filter "*.jar"

if ($jarFiles.Count -eq 0) {
    Write-Host "错误: 在 $DependenciesDir 中未找到 JAR 文件！" -ForegroundColor Red
    exit 1
}

$successCount = 0
$failCount = 0

foreach ($jarFile in $jarFiles) {
    $pomFile = $jarFile.FullName -replace '\.jar$', '.pom'
    
    if (-not (Test-Path $pomFile)) {
        Write-Host "警告: 未找到对应的 POM 文件: $pomFile" -ForegroundColor Yellow
        Write-Host "跳过: $($jarFile.Name)" -ForegroundColor Yellow
        $failCount++
        continue
    }
    
    $fileName = $jarFile.BaseName
    
    # 从文件名解析 artifactId 和 version
    # 格式：artifactId-version.jar
    # 例如：spring-ai-openai-spring-boot-starter-1.0.0-M4.jar
    if ($fileName -match '^(.+)-(\d+\.\d+\.\d+-M\d+)$') {
        $artifactId = $matches[1]
        $version = $matches[2]
        
        Write-Host "安装 $artifactId:$version..." -ForegroundColor Cyan
        
        $installCommand = "mvn install:install-file " +
            "-Dfile=`"$($jarFile.FullName)`" " +
            "-DgroupId=$GroupId " +
            "-DartifactId=$artifactId " +
            "-Dversion=$version " +
            "-Dpackaging=jar " +
            "-DpomFile=`"$pomFile`" " +
            "-DgeneratePom=false"
        
        try {
            Invoke-Expression $installCommand | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  ✓ 成功安装 $artifactId:$version" -ForegroundColor Green
                $successCount++
            } else {
                Write-Host "  ✗ 安装失败: $artifactId:$version (退出码: $LASTEXITCODE)" -ForegroundColor Red
                $failCount++
            }
        } catch {
            Write-Host "  ✗ 安装失败: $artifactId:$version" -ForegroundColor Red
            Write-Host "  错误: $_" -ForegroundColor Red
            $failCount++
        }
    } else {
        Write-Host "警告: 无法解析文件名格式: $fileName" -ForegroundColor Yellow
        Write-Host "  期望格式: artifactId-version.jar" -ForegroundColor Yellow
        $failCount++
    }
    
    Write-Host ""
}

Write-Host "=" * 50 -ForegroundColor Cyan
Write-Host "安装完成！" -ForegroundColor Green
Write-Host "成功: $successCount" -ForegroundColor Green
Write-Host "失败: $failCount" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Green" })
Write-Host "=" * 50 -ForegroundColor Cyan

if ($failCount -gt 0) {
    Write-Host ""
    Write-Host "提示: 如果安装失败，请检查：" -ForegroundColor Yellow
    Write-Host "1. Maven 是否正确安装并配置在 PATH 中" -ForegroundColor Yellow
    Write-Host "2. POM 文件是否存在且格式正确" -ForegroundColor Yellow
    Write-Host "3. 文件名是否符合 artifactId-version.jar 格式" -ForegroundColor Yellow
    exit 1
}





