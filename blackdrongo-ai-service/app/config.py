from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ai_service_name: str = "blackdrongo-ai-service"
    ai_service_port: int = 8001
    ai_provider: str = "mock"
    openai_api_key: str = ""
    openai_model: str = "gpt-4.1-mini"
    prompts_dir: str = "prompts"
    data_dir: str = "data"
    history_dir: str = "data/history"
    artifact_roots: str = "target,target/surefire-reports,target/cucumber-reports,reports"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()
