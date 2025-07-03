#!/usr/bin/env python3
"""
Text Extraction Script - Flask AI Service Client
This script calls the Flask AI service for OCR text extraction.
"""

import sys
import json
import os
import requests
import traceback

# FastAPI service configuration
FASTAPI_SERVICE_URL = os.getenv('FASTAPI_AI_SERVICE_URL', 'http://localhost:8001')

def extract_text_from_image(image_path):
    """
    Extract text from an image using Flask AI service.
    
    Args:
        image_path: Path to the image file
    
    Returns:
        dict: Extracted text and analysis results
    """
    try:
        # Validate image path
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Image not found: {image_path}")
        
        # Call FastAPI AI service
        response = requests.post(
            f"{FASTAPI_SERVICE_URL}/ai/ocr-extraction",
            json={
                "image_path": image_path
            },
            timeout=30
        )
        
        if response.status_code == 200:
            result = response.json()
            return {
                "raw_text": result.get("raw_text", ""),
                "cleaned_text": result.get("cleaned_text", ""),
                "license_info": result.get("license_info", {}),
                "confidence_scores": result.get("confidence_scores", []),
                "processing_method": result.get("processing_method", "easyocr")
            }
        else:
            error_msg = f"Flask service error: {response.status_code} - {response.text}"
            return {
                "raw_text": "",
                "cleaned_text": "",
                "license_info": {},
                "confidence_scores": [],
                "error": error_msg
            }
            
    except requests.exceptions.RequestException as e:
        error_msg = f"Failed to connect to Flask AI service: {str(e)}"
        return {
            "raw_text": "",
            "cleaned_text": "",
            "license_info": {},
            "confidence_scores": [],
            "error": error_msg
        }
    except Exception as e:
        error_msg = f"OCR extraction error: {str(e)}"
        return {
            "raw_text": "",
            "cleaned_text": "",
            "license_info": {},
            "confidence_scores": [],
            "error": error_msg
        }

def main():
    """Main function to handle command line arguments."""
    if len(sys.argv) != 2:
        error_result = {
            'success': False,
            'extracted_text': '',
            'license_info': {},
            'confidence_scores': [],
            'error': 'Invalid arguments. Usage: python textextract.py <image_path>',
            'message': 'Text extraction failed: Invalid arguments'
        }
        print(json.dumps(error_result))
        sys.exit(1)
    
    image_path = sys.argv[1]
    
    try:
        result = extract_text_from_image(image_path)
        
        # Convert Flask response to expected format
        formatted_result = {
            'success': True if not result.get('error') else False,
            'extracted_text': result.get('cleaned_text', result.get('raw_text', '')),
            'raw_text': result.get('raw_text', ''),
            'license_info': result.get('license_info', {}),
            'confidence_scores': result.get('confidence_scores', []),
            'processing_method': result.get('processing_method', 'easyocr'),
            'message': 'Text extraction completed successfully' if not result.get('error') else result.get('error')
        }
        
        if result.get('error'):
            formatted_result['error'] = result['error']
            formatted_result['success'] = False
        
        print(json.dumps(formatted_result))
        
    except Exception as e:
        error_result = {
            'success': False,
            'extracted_text': '',
            'license_info': {},
            'confidence_scores': [],
            'error': str(e),
            'error_type': type(e).__name__,
            'message': f'Text extraction failed: {str(e)}'
        }
        print(json.dumps(error_result))
        sys.exit(1)

if __name__ == "__main__":
    main()
