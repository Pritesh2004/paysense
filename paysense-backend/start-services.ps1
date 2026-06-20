$services = @("auth-service", "payment-service", "fraud-service", "notification-service", "transaction-service", "mcp-server")

foreach ($service in $services) {
    Write-Host "Starting $service..."
    Start-Process powershell -ArgumentList "-NoExit -Command cd '$service'; mvn spring-boot:run '-Dspring-boot.run.jvmArguments=-Xmx256m'" -WindowStyle Normal
}

Write-Host "All services are starting in separate windows."
