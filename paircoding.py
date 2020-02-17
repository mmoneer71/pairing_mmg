import pandas as pd
import numpy as np
import scipy.signal as sig
from scipy.fftpack import fftfreq, fft, ifft
from scipy.integrate import romb
from scipy.integrate import cumtrapz
import matplotlib.pyplot as plt
import smoothed_zscore as sz
import glob
import csv

# close possible previously created plots
plt.close('all')

# lists of files in the specified directory, one for watch sample, one for ohone samples
# change to your own directory
files_phone = glob.glob('Test_Data/Real_Beginning/logical_inch_conversion/Drawing_Data/*_smartphone_sample.csv') #['Test_Data/Real_Beginning/logical_inch_conversion/Drawing_Data/2020-02-04_3_smartphone_sample.csv']
files_watch = glob.glob('Test_Data/Real_Beginning/logical_inch_conversion/Accelerometer_Data/*_watch_sample.csv') #['Test_Data/Real_Beginning/logical_inch_conversion/Accelerometer_Data/2020-02-04_8_watch_sample.csv']

# DataFrame collection from files
data_phone = [pd.read_csv(x, engine='python') for x in files_phone] # List comprehension
data_watch = [pd.read_csv(x, engine='python') for x in files_watch]

# global variable for the cutoff frequency used in the low-pass filter
cutoff_freq = 50

# global viarable for the Grey-code similarity calculation 
# used by "grey_code_similarity" method
error_threshold = 0

# global variables used as parameters of Savitzky-Golay filers
window_length_savgol = 15
polyorder_savgol = 5

# dictionary for the Grey-code extraction
grey_code_dict =	{
  '1': '01',
  '0': '00',
  '-1': '11'
}

# jump in terms of datapoint used for extracting the grey code
jump = 2 

# lists needed for the cvs creation, subsequently used in the analisys.py script
acc_similarity_2b_list = list()
vel_similarity_2b_list = list()
acc_similarity_3b_list = list()
vel_similarity_3b_list = list()
watch_sample_names = list()
phone_sample_names = list()
should_match_list = list()


# utlity function for getting necessary info for the integral calculation
def f(x, key):
    if (x is None or key is None):
        ValueError(" f:  parameters must non-null ")
    index = pd.Index(data_watch[i]['timestamp']).get_loc(x)
    value = data_watch[i][key][index]
    return value

# integral calculated as the area beneath the graph, for each datapoint couple
def calculate_integral_list(timestampList, key):
    if (timestampList is None or len(timestampList) == 0 
        or key is None):
        ValueError(" f:  parameters must be non-null or >0 ")
    toCalc = np.array(timestampList.tolist())
    resultList = []
    resultList.append(0)
    for i in range(len(toCalc)):
        if (i == len(toCalc)-1): 
            break
        y1 = f(toCalc[i], key)
        y2 = f(toCalc[i + 1], key)
        integral = romb(np.array([y1,y2]), toCalc[i + 1] - toCalc[i])
        resultList.append(integral)
    return resultList

# derivative calculated as: dx/dt
def calculate_derivative_list(timestampList, toCalcList):
    if (timestampList is None or len(timestampList) == 0 or toCalcList is None 
        or len(toCalcList) == 0):
        ValueError(" calculate_derivative_list:  invalid parameters ")
    toCalc = np.asarray(toCalcList)
    resultList = []    
    resultList.append(0)
    for i in range(len(toCalc)):
        if i == len(toCalc)-1: 
            break
        resultList.append((toCalc[i + 1] - toCalc[i]) / (timestampList[i + 1] - timestampList[i]))
    return resultList

# function for first peak detection of a signal
# in this case we consider the first $window peaks 
# and consider the max (which corresponds to the initial tap on the screen)
def find_first_peak(peaks_array, orginal_array, window):
    if (peaks_array is None or len(peaks_array) == 0 or orginal_array is None 
        or len(orginal_array) == 0 or window <= 0):
        ValueError(" find_first_peak:  invalid parameters ")
    i = 0
    result = 0
    index = 0
    max_value = 0
    while(i < len(peaks_array) and index < window):        
        if(peaks_array[i] == 1):
            if (orginal_array[i] > max_value):
                max_value = orginal_array[i]
                result = i
            if (peaks_array[i - 1] != 1): # new peak ecountered
                index += 1           
        i += 1
    return result

# search for the closest value in a given array 
def find_nearest(array, value):
    if (a is None or len(a) == 0 or value is None):
        ValueError(" find_nearest:  invalid parameters ")
    array = np.asarray(array)
    idx = (np.abs(array - value)).argmin()
    return array[idx]

# rudimentary low-pass filter who removes frequencies above a cutoff threshold
def apply_lowpass_filter(a, cutoff_freq):
    if (a is None or len(a) == 0 or cutoff_freq is None 
        or cutoff_freq<0):
        ValueError(" apply_lowpass_filter:  invalid parameters ")
    a[cutoff_freq:] = 0
    return a

# returns the similarity (min 0, max 1) of the Grey-codes passed as parameters.
# This a two-pass algorithm: a, b are also inverted for the check.
# error_threshold: can be used to reject/accept the authentication
# max_window: is the max sliding window allowed for searching the best similarity (narrowing down lag problems among signals)
def grey_code_similarity(a, b, error_threshold, max_window):
    if (a is None or len(a) == 0 or b is None or len(b) == 0 
        or error_threshold is None or error_threshold < 0 or max_window is None 
        or max_window <0):
        ValueError(" grey_code_similarity:  invalid parameters ")
    return max(grey_code_similarity_split(a,b,max_window), grey_code_similarity_split(b,a,max_window))

# checks the 1-to-1 bit similarity of the two arrays
# The similarity is checked using a sliding windows 
# (i.e: the array b is shifted step-by-step)
def grey_code_similarity_split(a, b, max_window):
    if (a is None or len(a) == 0 or b is None or len(b) == 0 
        or max_window is None or max_window < 0):
        ValueError(" grey_code_similarity:  invalid parameters ")
    max_similarity = 0
    current_window = 0
    matching_bits = 0
    best_window = 0
    
    while(current_window <= max_window):
        i = current_window
        j = 0        
        while(i < len(a)):
            if (a[j] == b[i]):
                matching_bits += 1
            i += 1
            j += 1      
        if(matching_bits / (len(a) - current_window) > max_similarity):
            max_similarity = matching_bits / (len(a) - current_window)
            best_window = current_window        
        matching_bits = 0
        current_window += 1
    return max_similarity 

# a and b are supposed to be evenly long
# return a 2-bit Grey-code nparray of the encoded signals
# Rationale: the Cartesian coordinate system is divided in 4 quadrants
def grey_code_extraction_2bit(a, b):
    if (a is None or len(a) == 0 or b is None or len(b) == 0):
        ValueError(" grey_code_extraction:  invalid parameters ")
    i = 0
    bits_str = np.array([], dtype = str)
    while(i + jump < len(a) or i + jump < len(b)):        
        if (a[i + jump] - a[i] >= 0):
            bits_str = np.append(bits_str, '0')
            if (b[i + jump]- b[i] >= 0):
                bits_str = np.append(bits_str, '0')
            else:
                bits_str = np.append(bits_str, '1')
        else:
            bits_str = np.append(bits_str, '1')
            if (b[i + jump] - b[i] >= 0):
                bits_str = np.append(bits_str, '1')
            else:
                bits_str = np.append(bits_str, '0')
        i += 1
    return bits_str

# a and b are supposed to be evenly long
# return a 3-bit Grey-code nparray of the encoded signals
# Rationale: the Cartesian coordinate system is divided in 8 portions
def grey_code_extraction_3bit(a, b):
    if (a is None or len(a)==0 or b is None or len(b)==0):
        ValueError(" grey_code_extraction:  invalid parameters ")
    i = 0
    bits_str = np.array([], dtype = str)
    while(i + jump < len(a) or i + jump < len(b)):        
        if (a[i + jump] - a[i] >= 0) and (b[i + jump] - b[i] >= 0):
            if abs(b[i + jump] - b[i]) <= abs(a[i + jump] - a[i]):
                bits_str = np.append(bits_str, '000')
            else:
                bits_str = np.append(bits_str, '001')
        elif (a[i + jump] - a[i] < 0) and (b[i + jump] - b[i] >= 0):
            if abs(b[i + jump] - b[i]) > abs(a[i + jump] - a[i]):
                bits_str = np.append(bits_str, '011')
            else:
                bits_str = np.append(bits_str, '010')
        elif (a[i + jump] - a[i] < 0) and (b[i + jump] - b[i] < 0):
            if abs(b[i + jump] - b[i]) <= abs(a[i + jump] - a[i]):
                bits_str = np.append(bits_str, '110')
            else:
                bits_str = np.append(bits_str, '111')
        else:
            if abs(b[i + jump] - b[i]) > abs(a[i + jump] - a[i]):
                bits_str = np.append(bits_str, '101')
            else:
                bits_str = np.append(bits_str, '100')
        i += 1
    return bits_str


# === some comments are left for research purpose (e.g: in case they are needed for different tries)
# WATCH DATA ANALYSIS
if (files_watch is None or len(files_watch) < 0):
    ValueError(" WATCH DATA ANALYSIS:  files_watch parameter not valid ")
for i in range(len(files_watch)):
    
    #%% WATCH - PEAK DETECTION
    # in the filter chain the original signal gets less accurate step-by-step;
    # for each step of the filter chain a approximate accuracy estimation is given
    # e.g: after Savitzky-Golay filter the signal accuracy is 90%. Then, after
    # Smoothed z-score algorithm the signal accuracy gets lower, and so on.
    
    
    # 1st step: Savitzky-Golay filter - estimated final approximate accuracy = 90%
    # 2st step: Smoothed z-score algorithm -> estimated final approximate accuracy of the signal = low (since it returns a digital result)
    
    # calculate the linear acceleration for each axis
    # needed because without this step the z component would be too big
    data_watch[i]['temp_linear_x_acc'] = data_watch[i]['x_acc'] - data_watch[i]['x_acc'].mean()
    data_watch[i]['temp_linear_y_acc'] = data_watch[i]['y_acc'] - data_watch[i]['y_acc'].mean()
    data_watch[i]['temp_linear_z_acc'] = data_watch[i]['z_acc'] - data_watch[i]['z_acc'].mean()
    
    # acceleration noise filtering (through built-in Savitzky-Golay filter)
    data_watch[i]['filtered_x_acc'] = sig.savgol_filter(data_watch[i]['temp_linear_x_acc'], window_length_savgol, polyorder_savgol)
    data_watch[i]['filtered_y_acc'] = sig.savgol_filter(data_watch[i]['temp_linear_y_acc'], window_length_savgol, polyorder_savgol)
    data_watch[i]['filtered_z_acc'] = sig.savgol_filter(data_watch[i]['temp_linear_z_acc'], window_length_savgol, polyorder_savgol)
    
    # calculate the Euclidean norm = 2-norm (that is: magnitude)
    a = np.column_stack((data_watch[i]['filtered_x_acc'], data_watch[i]['filtered_y_acc'], data_watch[i]['filtered_z_acc']))
    b = np.zeros(a.shape[0])
    for j in range(a.shape[0]):
        b[j] = np.linalg.norm(a[j]) # Euclidean norm = 2-norm 
    data_watch[i]['magnitude'] = b
    
    # magnitude noise filtering (through built-in Savitzky-Golay filter)
    data_watch[i]['filtered_magnitude'] = sig.savgol_filter(b, window_length_savgol, polyorder_savgol)    
       
    # peak detection
    # y = data to analyze
    # lag = the lag of the moving window 
    # threshold = the z-score at which the algorithm signals and influence
    # influence = (between 0 and 1) of new signals on the mean and standard deviation
    smoothed_zscore = sz.thresholding_algo(data_watch[i]['filtered_magnitude'], 4, 9, 0) # thresholding_algo(y, lag, threshold, influence)
    data_watch[i]['filtered_magnitude_peaks'] = smoothed_zscore.get('signals')
    
    
    #%% WATCH - SIGNAL SYNCHRONISATION
    
    # first peak detection for synchronising smartwatch and smartphone signals
    # the last parameter indicates among how many initial peaks the maximum value should be detected
    # the maximum value represents the initial strong peak of the finger onto the smartphone
    firstpeak_index = find_first_peak(np.array(data_watch[i]['filtered_magnitude_peaks']), np.array(data_watch[i]['filtered_magnitude']),5) # find out the beginning of the very first peak
    #print(firstpeak_index, len(data_watch[i]['timestamp']), len(data_watch[i]['filtered_magnitude_peaks']))
    peaks_difference = data_watch[i]['timestamp'][firstpeak_index] - data_phone[i]['timestamp'][0] # time difference of the two devices' timestamps   
    data_watch[i]['timestamp'] = data_watch[i]['timestamp'] - peaks_difference # shift of the watch timestamps for synchronising the signals
    #print('Sync difference: ' + str(peaks_difference))
    
    # detect the timestamp interval on the smartwatch (goal: discard useless datapoints from the smartwatch samples i.e: before the first tap and after lifting the finger)
    diff = data_phone[i]['timestamp'].iloc[-1] - data_phone[i]['timestamp'][0] # drawing time on the smartphone screen
    approx_final_watch_timestamp = data_watch[i]['timestamp'][firstpeak_index] + diff # approximate timestamp of the last meaningful smartwatch movement
    final_watch_timestamp = find_nearest(data_watch[i]['timestamp'].values, approx_final_watch_timestamp) # actual timestamp of the last meaningful smartwatch movement
    final_index = pd.Index(data_watch[i]['timestamp']).get_loc(final_watch_timestamp) # index of actual timestamp of the last meaningful movement
    
    
    #%% WATCH - GRAVITY REMOVAL
    # 1st step: removal of the gravity mean for each of the axis only from the first to the final peak - final approximate accuracy = 80% 
    # 2st step: Savitzky-Golay filter - final approximate accuracy = 70%
    
    # calculate the linear acceleration for each axis (gravity removal in the interval firstpeak_index:final_index)    
    # this step is performed in order to be more accurate in the gravity removal
    data_watch[i]['linear_x_acc'] = data_watch[i]['x_acc'] - data_watch[i]['x_acc'][firstpeak_index:final_index+1].mean()
    data_watch[i]['linear_y_acc'] = data_watch[i]['y_acc'] - data_watch[i]['y_acc'][firstpeak_index:final_index+1].mean()
    data_watch[i]['linear_z_acc'] = data_watch[i]['z_acc'] - data_watch[i]['z_acc'][firstpeak_index:final_index+1].mean()
    
    # linear acceleration noise filtering (through built-in Savitzky-Golay filter)
    data_watch[i]['filtered_linear_x_acc'] = sig.savgol_filter(data_watch[i]['linear_x_acc'], window_length_savgol, polyorder_savgol)
    data_watch[i]['filtered_linear_y_acc'] = sig.savgol_filter(data_watch[i]['linear_y_acc'], window_length_savgol, polyorder_savgol)
    data_watch[i]['filtered_linear_z_acc'] = sig.savgol_filter(data_watch[i]['linear_z_acc'], window_length_savgol, polyorder_savgol)
    
    # FFT of the linear acceleration 
    data_watch[i]['watch_linear_x_acc_fft'] = fft(np.array(data_watch[i]['linear_x_acc']))
    data_watch[i]['watch_linear_y_acc_fft'] = fft(np.array(data_watch[i]['linear_y_acc']))
    # rudimentary low-pass filter on the FFT of the linear acceleration
    data_watch[i]['watch_linear_x_acc_fft_lp'] = apply_lowpass_filter(data_watch[i]['watch_linear_x_acc_fft'],cutoff_freq)
    data_watch[i]['watch_linear_y_acc_fft_lp'] = apply_lowpass_filter(data_watch[i]['watch_linear_y_acc_fft'],cutoff_freq)
    # Inverse FFT (lp stands for low-passed)
    data_watch[i]['watch_linear_x_acc_lp'] = ifft(data_watch[i]['watch_linear_x_acc_fft_lp'])
    data_watch[i]['watch_linear_y_acc_lp'] = ifft(data_watch[i]['watch_linear_y_acc_fft_lp'])
    
    
    #%% WATCH - VELOCITY CALCULATION
    # 1st step: "cumtrapz" integral calcolous starting from linear acceleration - final approximate accuracy = 80%
    # 2st step: Savitzky-Golay filter - final approximate accuracy = 70%
    
    # calculate velocity as the cumulative sum of the trapeziums beneath the crest
    temp_x_vel = np.full(len(data_watch[i]['timestamp']),np.nan)
    temp_y_vel = np.full(len(data_watch[i]['timestamp']),np.nan)
    temp_z_vel = np.full(len(data_watch[i]['timestamp']),np.nan)
    temp_x_vel[firstpeak_index:final_index+1] = cumtrapz(data_watch[i]['linear_x_acc'][firstpeak_index:final_index+1], data_watch[i]['timestamp'][firstpeak_index:final_index+1], initial=0)
    temp_y_vel[firstpeak_index:final_index+1] = cumtrapz(data_watch[i]['linear_y_acc'][firstpeak_index:final_index+1], data_watch[i]['timestamp'][firstpeak_index:final_index+1], initial=0)
    temp_z_vel[firstpeak_index:final_index+1] = cumtrapz(data_watch[i]['linear_z_acc'][firstpeak_index:final_index+1], data_watch[i]['timestamp'][firstpeak_index:final_index+1], initial=0)
    data_watch[i]['x_vel'] = temp_x_vel
    data_watch[i]['y_vel'] = temp_y_vel
    data_watch[i]['z_vel'] = temp_z_vel
    
    # velocity noise filtering (through built-in Savitzky-Golay filter)
    data_watch[i]['filtered_x_vel'] = sig.savgol_filter(data_watch[i]['x_vel'], window_length_savgol, polyorder_savgol)
    data_watch[i]['filtered_y_vel'] = sig.savgol_filter(data_watch[i]['y_vel'], window_length_savgol, polyorder_savgol)
    data_watch[i]['filtered_z_vel'] = sig.savgol_filter(data_watch[i]['z_vel'], window_length_savgol, polyorder_savgol)
    
    
    # FFT of the velocity    
    # Remove all the NaNs from the array replacing them with zero
    temp_x_vel = np.nan_to_num(temp_x_vel)
    temp_y_vel = np.nan_to_num(temp_y_vel)
    data_watch[i]['watch_x_vel_fft'] = fft(np.array(temp_x_vel))
    data_watch[i]['watch_y_vel_fft'] = fft(np.array(temp_y_vel))
    # rudimentary low-pass filter on the FFT of the velocity
    data_watch[i]['watch_x_vel_fft_lp'] = apply_lowpass_filter(data_watch[i]['watch_x_vel_fft'], cutoff_freq)
    data_watch[i]['watch_y_vel_fft_lp'] = apply_lowpass_filter(data_watch[i]['watch_y_vel_fft'], cutoff_freq)
    # Inverse FFT (lp stands for low-passed)
    data_watch[i]['watch_x_vel_lp'] = ifft(data_watch[i]['watch_x_vel_fft_lp'])
    data_watch[i]['watch_y_vel_lp'] = ifft(data_watch[i]['watch_y_vel_fft_lp'])
    
    
    #%% WATCH - POSITION CALCULATION
    # 1st step: "cumtrapz" integral calcolous starting from velocity - final approximate accuracy = 80%
    # 2st step: Savitzky-Golay filter - final approximate accuracy = 70%
    
    # calculate the position (as the cumulative sum of the velocity)
    temp_x_pos = np.full(len(data_watch[i]['timestamp']),np.nan)
    temp_y_pos = np.full(len(data_watch[i]['timestamp']),np.nan)
    temp_z_pos = np.full(len(data_watch[i]['timestamp']),np.nan)
    temp_x_pos[firstpeak_index:final_index+1] = cumtrapz(data_watch[i]['x_vel'][firstpeak_index:final_index+1], data_watch[i]['timestamp'][firstpeak_index:final_index+1], initial=0)
    temp_y_pos[firstpeak_index:final_index+1] = cumtrapz(data_watch[i]['y_vel'][firstpeak_index:final_index+1], data_watch[i]['timestamp'][firstpeak_index:final_index+1], initial=0)
    temp_z_pos[firstpeak_index:final_index+1] = cumtrapz(data_watch[i]['z_vel'][firstpeak_index:final_index+1], data_watch[i]['timestamp'][firstpeak_index:final_index+1], initial=0)
    data_watch[i]['x_pos'] = temp_x_pos
    data_watch[i]['y_pos'] = temp_y_pos
    data_watch[i]['z_pos'] = temp_z_pos
    
    # position noise filtering (through built-in Savitzky-Golay filter)
    data_watch[i]['filtered_x_pos'] = sig.savgol_filter(data_watch[i]['x_pos'], window_length_savgol, polyorder_savgol)
    data_watch[i]['filtered_y_pos'] = sig.savgol_filter(data_watch[i]['y_pos'], window_length_savgol, polyorder_savgol)
    data_watch[i]['filtered_z_pos'] = sig.savgol_filter(data_watch[i]['z_pos'], window_length_savgol, polyorder_savgol)
    
    
    #%% PHONE DATA ANALYSIS
    if (files_phone is None or len(files_phone)<0):
        ValueError(" PHONE DATA ANALYSIS:  files_phone parameter not valid ")
    for j in range(len(files_phone)):
            
        #%% PHONE - GET POSITION FROM API
        # approximate accuracy = 100%
        
        # scale x, y position (if needed add/remove the # and tweek the multiplicator)
        data_phone[j][['x_pos', 'y_pos']] = data_phone[j][['x', 'y']] #* 2000    
        # realign the y axis (smartphones have a different y-axis direction)
        data_phone[j]['y_pos'] = data_phone[j]['y_pos'] * -1
    
        #%% PHONE - GET VELOCITY FROM API (OR DERIVATING THE POSITION if needed)
        # 1st step: approximate accuracy = 100%
        # 2st step: Savitzky-Golay filter - final approximate accuracy = 90%
    
        # calculate the x,y velocity dervating the positions
#        data_phone[i]['x_vel'] = calculate_derivative_list(data_phone[i]['timestamp'], data_phone[i]['x_pos'])
#        data_phone[i]['y_vel'] = calculate_derivative_list(data_phone[i]['timestamp'], data_phone[i]['y_pos'])
         
        # get the x,y velocity from the MotionEvent API directly from the phone raw data
        data_phone[j][['x_vel', 'y_vel']] = data_phone[j][['x_velocity', 'y_velocity']]
#        data_phone[i]['y_vel'] = data_phone[i]['y_vel'] * -1        
        # scale x, y velocity (if needed add/remove the # and tweek the multiplicator)
        data_phone[j][['x_vel', 'y_vel']] = data_phone[j][['x_vel', 'y_vel']] / 150.0   
        # realign the y axis (smartphones have a different y-axis direction)
        data_phone[j]['y_vel'] = data_phone[j]['y_vel'] * -1
        
        # velocity noise filtering (through built-in Savitzky-Golay filter)
        data_phone[j]['filtered_x_vel'] = sig.savgol_filter(data_phone[j]['x_vel'], window_length_savgol, polyorder_savgol)
        data_phone[j]['filtered_y_vel'] = sig.savgol_filter(data_phone[j]['y_vel'], window_length_savgol, polyorder_savgol)
        
        # FFT of the velocity
        data_phone[j]['phone_x_vel_fft'] = fft(np.array(data_phone[j]['x_vel']))
        data_phone[j]['phone_y_vel_fft'] = fft(np.array(data_phone[j]['y_vel']))
        # rudimentary low-pass filter on the FFT of the velocity
        data_phone[j]['phone_x_vel_fft_lp'] = apply_lowpass_filter(data_phone[j]['phone_x_vel_fft'],cutoff_freq)
        data_phone[j]['phone_y_vel_fft_lp'] = apply_lowpass_filter(data_phone[j]['phone_y_vel_fft'],cutoff_freq)
        # Inverse FFT (lp stands for low-passed)
        data_phone[j]['phone_x_vel_lp'] = ifft(data_phone[j]['phone_x_vel_fft_lp'])
        data_phone[j]['phone_y_vel_lp'] = ifft(data_phone[j]['phone_y_vel_fft_lp'])
        
        
        #%% PHONE - ACCELERATION CALCULATION
        # 1st step: derivative calculation - approximate accuracy = 100%
        # 2st step: Savitzky-Golay filter - final approximate accuracy = 90%
        
        # calculate the acceleration (as the first-derivative of the velocity)
        data_phone[j]['x_acc'] = calculate_derivative_list(data_phone[j]['timestamp'], data_phone[j]['x_vel'])
        data_phone[j]['y_acc'] = calculate_derivative_list(data_phone[j]['timestamp'], data_phone[j]['y_vel']) 
        
        # scale x, y acceleration (if needed add/remove the # and tweek the multiplicator)
#        data_phone[i][['x_acc', 'y_acc']] = data_phone[i][['x_acc', 'y_acc']] * 10000000.0
        
        # acceleration noise filtering (through built-in Savitzky-Golay filter)
        data_phone[j]['filtered_x_acc'] = sig.savgol_filter(data_phone[j]['x_acc'], window_length_savgol, polyorder_savgol)
        data_phone[j]['filtered_y_acc'] = sig.savgol_filter(data_phone[j]['y_acc'], window_length_savgol, polyorder_savgol)
        
        # FFT of the acceleration 
        data_phone[j]['phone_x_acc_fft'] = fft(np.array(data_phone[j]['x_acc']))
        data_phone[j]['phone_y_acc_fft'] = fft(np.array(data_phone[j]['y_acc']))
        # rudimentary low-pass filter on the FFT of the acceleration
        data_phone[j]['phone_x_acc_fft_lp'] = apply_lowpass_filter(data_phone[j]['phone_x_acc_fft'],cutoff_freq)
        data_phone[j]['phone_y_acc_fft_lp'] = apply_lowpass_filter(data_phone[j]['phone_y_acc_fft'],cutoff_freq)
        # Inverse FFT (lp stands for low-passed)
        data_phone[j]['phone_x_acc_lp'] = ifft(data_phone[j]['phone_x_acc_fft_lp']) * 3
        data_phone[j]['phone_y_acc_lp'] = ifft(data_phone[j]['phone_y_acc_fft_lp']) * 3
        
        
        #%% GREY-CODE EXTRACTION
        
        #   ================================== RESAMPLING =======================================
        # in order to have the same code length it is necessary to RESAMPLE THE WATCH SIGNALS
        # N.B: accelerometer usually samples at a higher frequency than the Android Motion API
        phone_row_number = data_phone[j].shape[0]
        max_greycode_window = int(phone_row_number/20)
        watch_linear_x_acc_lp_resampled = sig.resample(data_watch[i]['watch_linear_x_acc_lp'][firstpeak_index:final_index+1], phone_row_number)
        watch_linear_y_acc_lp_resampled = sig.resample(data_watch[i]['watch_linear_y_acc_lp'][firstpeak_index:final_index+1], phone_row_number)
        watch_x_vel_lp_resampled = sig.resample(data_watch[i]['watch_x_vel_lp'][firstpeak_index:final_index+1], phone_row_number)
        watch_y_vel_lp_resampled = sig.resample(data_watch[i]['watch_y_vel_lp'][firstpeak_index:final_index+1], phone_row_number)
        
        
        #   ============================== GREY CODE EXTRACTION =================================
        #    ============================= 2 bit =============================    
        # Grey-code extraction - WATCH
        watch_acc_greycode_2bit = grey_code_extraction_2bit(watch_linear_x_acc_lp_resampled, watch_linear_y_acc_lp_resampled)
        watch_vel_greycode_2bit = grey_code_extraction_2bit(watch_x_vel_lp_resampled, watch_y_vel_lp_resampled)
        
        # Grey-code extraction - PHONE
        phone_acc_greycode_2bit = grey_code_extraction_2bit(data_phone[j]['phone_x_acc_lp'], data_phone[j]['phone_y_acc_lp'])
        phone_vel_greycode_2bit = grey_code_extraction_2bit(data_phone[j]['phone_x_vel_lp'], data_phone[j]['phone_y_vel_lp'])
        
        # Calculate the percentage of similar bits there in the two codes, using a sliding window
        similarity_acc_2bit = grey_code_similarity(watch_acc_greycode_2bit, phone_acc_greycode_2bit, 0, max_greycode_window)
        similarity_vel_2bit = grey_code_similarity(watch_vel_greycode_2bit, phone_vel_greycode_2bit, 0, max_greycode_window)
        
        #    ============================= 3 bit =============================
        # Grey-code extraction - WATCH
        watch_acc_greycode_3bit = grey_code_extraction_3bit(watch_linear_x_acc_lp_resampled, watch_linear_y_acc_lp_resampled)
        watch_vel_greycode_3bit = grey_code_extraction_3bit(watch_x_vel_lp_resampled, watch_y_vel_lp_resampled)
        
        # Grey-code extraction - PHONE
        phone_acc_greycode_3bit = grey_code_extraction_3bit(data_phone[j]['phone_x_acc_lp'], data_phone[j]['phone_y_acc_lp'])
        phone_vel_greycode_3bit = grey_code_extraction_3bit(data_phone[j]['phone_x_vel_lp'], data_phone[j]['phone_y_vel_lp'])
        
        # Calculate the percentage of similar bits there in the two codes, using a sliding window
        similarity_acc_3bit = grey_code_similarity(watch_acc_greycode_3bit, phone_acc_greycode_3bit, 0, max_greycode_window*1.5) # 1.5 times in order to have the same proportion between 2-bit a 3-bit code windows
        similarity_vel_3bit = grey_code_similarity(watch_vel_greycode_3bit, phone_vel_greycode_3bit, 0, max_greycode_window*1.5)
    
        #%% START PLOTTING
#        fig, (ax1, ax2) = plt.subplots(2, 1) #used for 2D drawings
#        fig2, (ax3, ax4) = plt.subplots(2, 1) #used for time-domain plots

        #%% PLOT ACCELERATIONS (in order to have 2D drwaing-like plots)
    
    #    plt.figure()
    #    tmp_init_ind = 130
    #    tmp_final_ind = 170
    #    
    #    plt.plot(data_phone[i]['phone_x_acc_lp'][tmp_init_ind:tmp_final_ind]*3,data_phone[i]['phone_y_acc_lp'][tmp_init_ind:tmp_final_ind]*3, color='r', alpha=0.7, label='Phone accelerations')
    #    plt.plot(watch_linear_x_acc_lp_resampled[tmp_init_ind:tmp_final_ind],watch_linear_y_acc_lp_resampled[tmp_init_ind:tmp_final_ind], color='b', alpha=0.3, label='Watch accelerations')
    #    
    #    plt.xlabel('x_acc after lpf')
    #    plt.ylabel('y_acc after lpf')
    #    plt.title('2D Plot - Accelerations')
    #    
    #    ax1.set_title('X Accelerations in the time domain - after FFT')
    #    ax1.plot(np.arange(phone_row_number), data_phone[i]['phone_x_acc_lp']*3, color='r', alpha=0.7, label='Phone x_acc lowpassed')
    #    ax1.plot(np.arange(phone_row_number), watch_linear_x_acc_lp_resampled, color='b', alpha=0.3, label='Watch x_acc lowpassed')
    #    ax1.set_xlabel('Timestamp')
    #    ax1.set_ylabel('Amplitude')
    #    ax1.legend()
    #    
    #    ax2.set_title('Y Accelerations in the time domain - after FFT')
    #    ax2.plot(np.arange(phone_row_number), data_phone[i]['phone_y_acc_lp']*3, color='r', alpha=0.7, label='Phone y_acc lowpassed')
    #    ax2.plot(np.arange(phone_row_number), watch_linear_y_acc_lp_resampled, color='b', alpha=0.3, label='Watch y_acc lowpassed')
    #    ax2.set_xlabel('Timestamp')
    #    ax2.set_ylabel('Amplitude')
    #    ax2.legend()
    
        #%% PLOT VELOCITIES (in order to have 2D drwaing-like plots)
        
#        plt.figure()
#        tmp_init_ind = 0
#        tmp_final_ind = 10
#        
#        plt.plot(data_phone[j]['phone_x_vel_lp'][tmp_init_ind:tmp_final_ind]*3,data_phone[j]['phone_y_vel_lp'][tmp_init_ind:tmp_final_ind]*3, color='r', alpha=0.7)
#        plt.plot(watch_x_vel_lp_resampled[tmp_init_ind:tmp_final_ind],watch_y_vel_lp_resampled[tmp_init_ind:tmp_final_ind], color='b', alpha=0.3)
#        
#        plt.xlabel('x_vel after lpf')
#        plt.ylabel('y_vel after lpf')
#        plt.title('2D Plot - Velocities')
#        
#        ax1.set_title('X Velocities in the time domain')
#        ax1.plot(np.arange(phone_row_number), data_phone[j]['phone_x_vel_lp']*3, color='r', alpha=0.7, label='Phone x_vel lowpassed')
#        ax1.plot(np.arange(phone_row_number), watch_x_vel_lp_resampled, color='b', label='Watch x_vel lowpassed')
#        ax1.set_xlabel('Timestamp')
#        ax1.set_ylabel('Amplitude')
#        ax1.legend()
#        
#        ax2.set_title('Y Velocities in the time domain')
#        ax2.plot(np.arange(phone_row_number), data_phone[j]['phone_y_vel_lp']*3, color='r', alpha=0.7, label='Phone y_vel lowpassed')
#        ax2.plot(np.arange(phone_row_number), watch_y_vel_lp_resampled, color='b', label='Watch y_vel lowpassed')
#        ax2.set_xlabel('Timestamp')
#        ax2.set_ylabel('Amplitude')
#        ax2.legend()
    
    
        #%% SMARTPHONE DATA PLOTTING (in order to have 2D drwaing-like plots)   
        
#        data_phone[i][[
#                'timestamp',
#                
#    #            'x_pos', 
#    #            'y_pos',
#                           
#    #            'filtered_x_vel',
#    #            'filtered_y_vel',
#                
#    #            'x_vel',
#    #            'y_vel',
#                
##                'filtered_x_acc',
#    #            'filtered_y_acc',
#                
#                'x_acc',
#    #            'y_acc'
#                
#                ]].plot(ax=ax3, x='timestamp', color='r')
    
        #%% SMARTWATCH DATA PLOTTING (in order to have 2D drwaing-like plots)
        
        # needed for adding title to plots
        path = files_phone[j].split("\\")
        file_date = path[-1].split("_")[0]
        file_id = path[-1].split("_")[1]
        title = "File ID: " + ''.join(file_date) + "_" + ''.join(file_id)
        
#        data_watch[i][[
#                'timestamp',
#        
#    #            'filtered_x_pos',
#    #            'filtered_y_pos',
#    #            'filtered_z_pos',
#                
#    #            'x_pos', 
#    #            'y_pos', 
#    #            'z_pos',
#        
#    #            'filtered_x_vel',
#    #            'filtered_y_vel',
#    #            'filtered_z_vel',
#    #            
#    #            'x_vel', 
#    #            'y_vel', 
#    #            'z_vel',
#        
#    #            'filtered_linear_x_acc', 
#    #            'filtered_linear_y_acc', 
#    #            'filtered_linear_z_acc',
#                
#                'linear_x_acc', 
#    #            'linear_y_acc', 
#    #            'linear_z_acc',
#    #            
#    #            'filtered_x_acc',
#    #            'filtered_y_acc',
#    #            'filtered_z_acc',
#                
##                'x_acc',
#    #            'y_acc',
#    #            'z_acc',
#        
#    #            'magnitude',
##                'filtered_magnitude',
##                'filtered_magnitude_peaks'
#                
#                ]].plot(ax=ax3, x='timestamp',  title=title+'\n\nPlot - before FFT')
#        
#        ax3.set_xlabel('Timestamp')
#        ax3.set_ylabel('Amplitude')
        
        #%% SMARTWATCH-SMARTPHONE FFT/IFFT DATA PLOTTING
        
#        data_phone[j][[
#                'timestamp',
#                
#    #            'phone_x_vel_fft',
#    #            'phone_y_vel_fft',
#    #            'phone_x_vel_fft_lp',
#    #            'phone_y_vel_fft_lp',
#    #            'phone_x_vel_lp',
#    #            'phone_y_vel_lp',
#    #            
#    #            'phone_x_acc_fft',
#    #            'phone_y_acc_fft',
#    #            'phone_x_acc_fft_lp',
#    #            'phone_y_acc_fft_lp',
##                'phone_x_acc_lp',
#                'phone_y_acc_lp',k_index:final_index+1], phone_row_number)
        watch_x_vel_lp_resampled = sig.resample(data_watch[i]['watch_x_vel_lp'][firstpeak_index:final_index+1], phone_row_number)
        watch_y_vel_lp_resampled = sig.resample(data_watch[i]['watch_y_vel_lp'][firstpeak_index:final_index+1], phone_row_number)
        
#                
#        ]].plot(ax=ax4, label='phone', x='timestamp', color='r', alpha = 0.7)
        
    
#        data_watch[i][[
#                'timestamp',cutoff_freq =
#                
#    #            'watch_x_vel_fft',
#    #            'watch_y_vel_fft',
#    #            'watch_x_vel_fft_lp',
#    #            'watch_y_vel_fft_lp',
#    #            'watch_x_vel_lp',
#    #            'watch_y_vel_lp',
#    #            
#    #            'watch_linear_x_acc_fft',
#    #            'watch_linear_y_acc_fft',
#    #            'watch_linear_x_acc_fft_lp',
#    #            'watch_linear_y_acc_fft_lp',
##                'watch_linear_x_acc_lp',
#                'watch_linear_y_acc_lp',
#                
#        ]].plot(ax=ax4, title='\n\nPlot - after FFT', label='watch', x='timestamp', color='b', alpha = 0.7)
#        
#        ax4.set_xlabel('Timestamp')
#        ax4.set_ylabel('Amplitude')
#        
#        plt.legend()    
#        plt.show()
        
        # console print
        print(
                "Similarity of accelerations (2bit):" + str(similarity_acc_2bit),
                "\nSimilarity of velocities (2bit):" + str(similarity_vel_2bit),
                "\nSimilarity of accelerations (3bit):" + str(similarity_acc_3bit),
                "\nSimilarity of velocities (3bit):" + str(similarity_vel_3bit),
                "\nError threshold: " + str(error_threshold),
                "\nMax window: " + str(max_greycode_window)
                )
        
        # needed for the result csv file
        watch_id = files_watch[i].split("/")[-1]
        phone_id = files_phone[j].split("/")[-1]

        # watch_sample and phone_sample list generation
        watch_sample_names.append(watch_id)
        phone_sample_names.append(phone_id)
        
        # class list generation (the two samples should/shouldn't match)
        if (watch_id.split('_')[1] == phone_id.split('_')[1]):
            should_match_list.append('yes')
        else:
            should_match_list.append('no')
        
        # code similarity lists generation
        acc_similarity_2b_list.append(similarity_acc_2bit)
        vel_similarity_2b_list.append(similarity_vel_2bit)
        acc_similarity_3b_list.append(similarity_acc_3bit)
        vel_similarity_3b_list.append(similarity_vel_3bit)


#%% analysis_result.csv FILE CREATION
with open('\\'.join(path[0:-1])+'analysis_result.csv', 'w', newline='') as mycsvfile:
    wr = csv.writer(mycsvfile)
    wr.writerow(['watch_sample','phone_sample','class',
                 'acc_similarity_2b','vel_similarity_2b','acc_similarity_3b',
                 'vel_similarity_3b'])
    for i in range(len(watch_sample_names)):
        wr.writerow([watch_sample_names[i],phone_sample_names[i],should_match_list[i],
                     acc_similarity_2b_list[i],vel_similarity_2b_list[i],acc_similarity_3b_list[i],
                     vel_similarity_3b_list[i]])   
