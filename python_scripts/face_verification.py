#!/usr/bin/env python3
"""
Face Verification Script - FastAPI AI Service Client
This script calls the FastAPI AI service for face verification.
"""

import sys
import json
import os
import requests
import traceback

# FastAPI service configuration
FASTAPI_SERVICE_URL = os.getenv('FASTAPI_AI_SERVICE_URL', 'http://localhost:8001')

def verify_faces(image1_path, image2_path):
    """
    Verify if two faces belong to the same person using FastAPI AI service.
    
    Args:
        image1_path: Path to the first image
        image2_path: Path to the second image
    
    Returns:
        dict: Face verification result with similarity score
    """
    try:
        # Validate image paths
        if not os.path.exists(image1_path):
            raise FileNotFoundError(f"First image not found: {image1_path}")
        if not os.path.exists(image2_path):
            raise FileNotFoundError(f"Second image not found: {image2_path}")
        
        # Call FastAPI AI service
        response = requests.post(
            f"{FASTAPI_SERVICE_URL}/ai/face-verification",
            json={
                "image1_path": image1_path,
                "image2_path": image2_path
            },
            timeout=30
        )
        
        if response.status_code == 200:
            result = response.json()
            return {
                "verified": result.get("verified", False),
                "similarity_score": result.get("similarity_score", 0.0),
                "confidence": result.get("confidence", 0.0),
                "threshold": result.get("threshold", 0.6),
                "processing_method": result.get("processing_method", "deepface")
            }
        else:
            error_msg = f"FastAPI service error: {response.status_code} - {response.text}"
            return {
                "verified": False,
                "similarity_score": 0.0,
                "confidence": 0.0,
                "error": error_msg
            }
    
    except requests.exceptions.ConnectionError:
        return {
            "verified": False,
            "similarity_score": 0.0,
            "confidence": 0.0,
            "error": "FastAPI AI service not running",
            "message": "Could not connect to FastAPI AI service. Please start it first."
        }
    except Exception as e:
        return {
            "verified": False,
            "similarity_score": 0.0,
            "confidence": 0.0,
            "error": str(e),
            "error_type": type(e).__name__,
            "message": f"Face verification failed: {str(e)}"
        }

def main():
    """Main function to handle command line arguments."""
    if len(sys.argv) != 3:
        error_result = {
            "verified": False,
            "similarity_score": 0.0,
            "confidence": 0.0,
            "error": "Invalid arguments. Usage: python face_verification.py <image1_path> <image2_path>",
            "message": "Face verification failed: Invalid arguments"
        }
        print(json.dumps(error_result))
        sys.exit(1)
    
    image1_path = sys.argv[1]
    image2_path = sys.argv[2]
    
    try:
        result = verify_faces(image1_path, image2_path)
        
        # Check if confidence is more than 0.5 and print true/false
        if "error" not in result:
            confidence = result.get("confidence", 0.0)
            if confidence > 0.5:
                print("true")
            else:
                print("false")
            sys.exit(0)
        else:
            # Print the full error result for debugging
            print(json.dumps(result))
            sys.exit(1)
        
    except Exception as e:
        error_result = {
            "verified": False,
            "similarity_score": 0.0,
            "confidence": 0.0,
            "error": str(e),
            "error_type": type(e).__name__,
            "traceback": traceback.format_exc(),
            "message": f"Face verification failed: {str(e)}"
        }
        print(json.dumps(error_result))
        sys.exit(1)

if __name__ == "__main__":
    main()