#!/usr/bin/env python3
"""
FastAPI AI Service Setup and Test
"""

import subprocess
import sys
import time
import os

def install_requirements():
    """Install FastAPI requirements."""
    print("📦 Installing FastAPI requirements...")
    try:
        result = subprocess.run([
            sys.executable, "-m", "pip", "install", "-r", "requirements_fastapi.txt"
        ], check=True, capture_output=True, text=True)
        print("✅ Requirements installed successfully!")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ Failed to install requirements: {e}")
        print(f"Error output: {e.stderr}")
        return False

def main():
    """Setup and test FastAPI AI service."""
    print("🚀 FastAPI AI Service Setup")
    print("=" * 35)
    
    # Install requirements
    if not install_requirements():
        return False
    
    print("\n🧪 Testing FastAPI service...")
    try:
        # Run the test
        result = subprocess.run([
            sys.executable, "test_fastapi_service.py"
        ], check=False)
        
        return result.returncode == 0
        
    except Exception as e:
        print(f"❌ Test failed: {e}")
        return False

if __name__ == "__main__":
    success = main()
    
    if success:
        print("\n🎉 FastAPI AI Service is ready!")
        print("📚 Features available:")
        print("   ⚡ Lightning-fast async API")
        print("   📖 Interactive docs at /docs")
        print("   🔧 Automatic validation")
        print("   💾 Low memory usage in mock mode")
        print("\n🚀 Start the service:")
        print("   python fastapi_ai_service.py")
    else:
        print("\n❌ Setup failed. Please check the errors above.")
    
    sys.exit(0 if success else 1)
