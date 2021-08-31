// This file is part of OpenCV project.
// It is subject to the license terms in the LICENSE file found in the top-level directory
// of this distribution and at http://opencv.org/license.html.

#ifndef OPENCV_QUALITYBASE_HPP
#define OPENCV_QUALITYBASE_HPP

#include <opencv2/core.hpp>

/**
@defgroup quality Image Quality Analysis (IQA) API
*/

namespace cv {
    namespace quality {

//! @addtogroup quality
//! @{

/************************************ Quality Base Class ************************************/
        class CV_EXPORTS_W QualityBase

        : public virtual Algorithm {
        public:

        /** @brief Destructor */
        virtual ~

        QualityBase() = default;

        /**
        @brief Compute quality score per channel with the per-channel score in each element of the resulting cv::Scalar.  See specific algorithm for interpreting result scores
        @param img comparison image, or image to evalute for no-reference quality algorithms
        */
        virtual CV_WRAP cv::Scalar
        compute( InputArray
        img ) = 0;

        /** @brief Returns output quality map that was generated during computation, if supported by the algorithm  */
        virtual CV_WRAP void getQualityMap(OutputArray dst) const {
            if (!dst.needed() || _qualityMap.empty())
                return;
            dst.assign(_qualityMap);
        }

        /** @brief Implements Algorithm::clear()  */
        CV_WRAP void clear()

        CV_OVERRIDE {
        _qualityMap = _mat_type();

        Algorithm::clear();
    }

    /** @brief Implements Algorithm::empty()  */
    CV_WRAP bool empty() const

    CV_OVERRIDE {
    return _qualityMap.

    empty();
}

protected:

/** @brief internal mat type default */
using _mat_type = cv::UMat;

/** @brief Output quality maps if generated by algorithm */
_mat_type _qualityMap;

};  // QualityBase
//! @}
}   // quality
}   // cv
#endif