import logging

import uvicorn

from app.api import create_app
from app.config import get_settings
from app.container import build_services, provider_configuration


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
    settings = get_settings()
    logging.getLogger(__name__).info("Feedback providers: %s", provider_configuration(settings))
    uvicorn.run(create_app(build_services(settings)), host=settings.ai_server_address, port=settings.ai_server_port)


if __name__ == "__main__":
    main()
