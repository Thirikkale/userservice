#!/usr/bin/env python3
"""
Gender Detection Script using DeepFace
This script analyzes a face image and predicts the gender.
"""

import sys
import json
import os
import requests
import traceback

# FastAPI AI Service URL
FASTAPI_AI_URL = os.getenv('FASTAPI_AI_SERVICE_URL', 'http://127.0.0.1:8001')

# Note: This function is no longer needed since we're using Flask AI service
# def load_image(image_path):
#     """Load and validate image."""
#     if not os.path.exists(image_path):
#         raise FileNotFoundError(f"Image not found: {image_path}")
#     
#     return image_path

def detect_gender(image_path):
    """
    Detect gender via Flask AI service.
    
    Args:
        image_path: Path to the image file
    
    Returns:
        dict: Gender detection result with confidence scores
    """
    try:
        # Validate image path
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Image not found: {image_path}")
        
        # Call FastAPI AI service
        response = requests.post(
            f"{FASTAPI_AI_URL}/ai/gender-detection",
            json={"image_path": image_path},
            timeout=30
        )
        
        if response.status_code == 200:
            return response.json()
        else:
            error_data = response.json() if response.headers.get('content-type') == 'application/json' else {}
            return {
                'success': False,
                'predicted_gender': 'Unknown',
                'confidence_score': 0.0,
                'error': error_data.get('error', f'HTTP {response.status_code}'),
                'message': f'Flask AI service error: {response.status_code}'
            }
        
    except requests.exceptions.ConnectionError:
        return {
            'success': False,
            'predicted_gender': 'Unknown',
            'confidence_score': 0.0,
            'error': 'FastAPI AI service not running',
            'message': 'Could not connect to FastAPI AI service. Please start it first.'
        }
    except Exception as e:
        return {
            'success': False,
            'predicted_gender': 'Unknown',
            'confidence_score': 0.0,
            'error': str(e),
            'error_type': type(e).__name__,
            'message': f'Gender detection failed: {str(e)}'
        }

def main():
    """Main function to handle command line arguments."""
    if len(sys.argv) != 2:
        error_result = {
            'success': False,
            'predicted_gender': 'Unknown',
            'confidence_score': 0.0,
            'error': 'Invalid arguments. Usage: python gender.py <image_path>',
            'message': 'Gender detection failed: Invalid arguments'
        }
        print(json.dumps(error_result))
        sys.exit(1)
    
    image_path = sys.argv[1]
    
    try:
        result = detect_gender(image_path)
        print(json.dumps(result))
        
        # Exit with appropriate code
        sys.exit(0 if result['success'] else 1)
        
    except Exception as e:
        error_result = {
            'success': False,
            'predicted_gender': 'Unknown',
            'confidence_score': 0.0,
            'error': str(e),
            'error_type': type(e).__name__,
            'traceback': traceback.format_exc(),
            'message': f'Gender detection failed: {str(e)}'
        }
        print(json.dumps(error_result))
        sys.exit(1)

if __name__ == "__main__":
    main()
