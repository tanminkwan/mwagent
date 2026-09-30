"""
Biz Service Configuration
"""
import os

# JWT Configuration
JWT_SECRET_KEY = os.environ.get('JWT_SECRET_KEY', 'test-secret-key-for-mock-server')
JWT_ALGORITHM = 'HS256'
JWT_ISSUER = 'mwm-auth-server'
JWT_AUDIENCE = 'https://api.mwagent.example.com'

# Redis Configuration (optional - for token validation)
REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))
REDIS_DB = int(os.environ.get('REDIS_DB', 0))
USE_REDIS = os.environ.get('USE_REDIS', 'false').lower() == 'true'

# Server Configuration
HOST = os.environ.get('BIZ_SERVICE_HOST', '0.0.0.0')
PORT = int(os.environ.get('BIZ_SERVICE_PORT', 5000))
DEBUG = os.environ.get('BIZ_SERVICE_DEBUG', 'true').lower() == 'true'
