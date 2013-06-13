(ns taoensso.nippy.tests.main
  (:require [expectations   :as test :refer :all]
            [taoensso.nippy :as nippy :refer (freeze thaw)]
            [taoensso.nippy.benchmarks :as benchmarks]))

;; Remove stuff from stress-data that breaks roundtrip equality
(def test-data (dissoc nippy/stress-data :bytes))

(def roundtrip-defaults         (comp thaw freeze))
(def roundtrip-encrypted        (comp #(thaw   % {:password [:salted "p"]})
                                      #(freeze % {:password [:salted "p"]})))
(def roundtrip-defaults-legacy  (comp #(thaw   % {:legacy-mode? true})
                                      #(freeze % {:legacy-mode? true})))
(def roundtrip-encrypted-legacy (comp #(thaw   % {:password [:salted "p"]
                                                  :legacy-mode? true})
                                      #(freeze % {:password [:salted "p"]
                                                  :legacy-mode? true})))

;;; Basic data integrity
(expect test-data (roundtrip-defaults         test-data))
(expect test-data (roundtrip-encrypted        test-data))
(expect test-data (roundtrip-defaults-legacy  test-data))
(expect test-data (roundtrip-encrypted-legacy test-data))

(expect ; Snappy lib compatibility (for legacy versions of Nippy)
 (let [^bytes raw-ba    (freeze test-data {:compressor nil})
       ^bytes xerial-ba (org.xerial.snappy.Snappy/compress raw-ba)
       ^bytes iq80-ba   (org.iq80.snappy.Snappy/compress   raw-ba)]
   (= (thaw raw-ba)
      (thaw (org.xerial.snappy.Snappy/uncompress xerial-ba))
      (thaw (org.xerial.snappy.Snappy/uncompress iq80-ba))
      (thaw (org.iq80.snappy.Snappy/uncompress   iq80-ba    0 (alength iq80-ba)))
      (thaw (org.iq80.snappy.Snappy/uncompress   xerial-ba  0 (alength xerial-ba))))))

;;; API stuff

;; Strict/auto mode - compression
(expect test-data (thaw (freeze test-data {:compressor nil})))
(expect Exception (thaw (freeze test-data {:compressor nil}) {:strict? true}))

;; Strict/auto mode - encryption
(expect test-data (thaw (freeze test-data) {:password [:salted "p"]}))
(expect Exception (thaw (freeze test-data) {:password [:salted "p"] :strict? true}))

;; Encryption - passwords
(expect Exception (thaw (freeze test-data {:password "malformed"})))
(expect Exception (thaw (freeze test-data {:password [:salted "p"]})))
(expect test-data (thaw (freeze test-data {:password [:salted "p"]})
                        {:password [:salted "p"]}))

(expect (benchmarks/autobench)) ; Also tests :cached passwords