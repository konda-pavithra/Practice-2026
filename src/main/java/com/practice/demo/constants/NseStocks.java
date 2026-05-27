package com.practice.demo.constants;

import java.util.List;
import java.util.Map;

/**
 * NSE Nifty 50 stock symbols in Yahoo Finance format (.NS suffix).
 * Update this list whenever NSE revises the Nifty 50 index composition.
 */
public final class NseStocks {

    private NseStocks() {}

    /** Yahoo Finance symbols — passed directly to the quote API. */
    public static final List<String> SYMBOLS = List.of(
            "RELIANCE.NS",   "TCS.NS",         "HDFCBANK.NS",   "BHARTIARTL.NS", "ICICIBANK.NS",
            "INFY.NS",       "SBIN.NS",         "HINDUNILVR.NS", "ITC.NS",        "LT.NS",
            "BAJFINANCE.NS", "KOTAKBANK.NS",    "AXISBANK.NS",   "MARUTI.NS",     "ASIANPAINT.NS",
            "WIPRO.NS",      "HCLTECH.NS",      "ULTRACEMCO.NS", "TITAN.NS",      "SUNPHARMA.NS",
            "TATAMOTORS.NS", "NTPC.NS",         "POWERGRID.NS",  "TATASTEEL.NS",  "ONGC.NS",
            "BAJAJFINSV.NS", "GRASIM.NS",       "COALINDIA.NS",  "DIVISLAB.NS",   "DRREDDY.NS",
            "CIPLA.NS",      "EICHERMOT.NS",    "HEROMOTOCO.NS", "BPCL.NS",       "ADANIPORTS.NS",
            "JSWSTEEL.NS",   "HINDALCO.NS",     "INDUSINDBK.NS", "TECHM.NS",      "SBILIFE.NS",
            "HDFCLIFE.NS",   "APOLLOHOSP.NS",   "ADANIENT.NS",   "LTIM.NS",       "BAJAJ-AUTO.NS",
            "BEL.NS",        "TRENT.NS",        "SHRIRAMFIN.NS", "M&M.NS",        "NESTLEIND.NS"
    );

    /** Human-readable company names keyed by Yahoo Finance symbol. */
    public static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("RELIANCE.NS",   "Reliance Industries"),
            Map.entry("TCS.NS",        "Tata Consultancy Services"),
            Map.entry("HDFCBANK.NS",   "HDFC Bank"),
            Map.entry("BHARTIARTL.NS", "Bharti Airtel"),
            Map.entry("ICICIBANK.NS",  "ICICI Bank"),
            Map.entry("INFY.NS",       "Infosys"),
            Map.entry("SBIN.NS",       "State Bank of India"),
            Map.entry("HINDUNILVR.NS", "Hindustan Unilever"),
            Map.entry("ITC.NS",        "ITC Limited"),
            Map.entry("LT.NS",         "Larsen & Toubro"),
            Map.entry("BAJFINANCE.NS", "Bajaj Finance"),
            Map.entry("KOTAKBANK.NS",  "Kotak Mahindra Bank"),
            Map.entry("AXISBANK.NS",   "Axis Bank"),
            Map.entry("MARUTI.NS",     "Maruti Suzuki"),
            Map.entry("ASIANPAINT.NS", "Asian Paints"),
            Map.entry("WIPRO.NS",      "Wipro"),
            Map.entry("HCLTECH.NS",    "HCL Technologies"),
            Map.entry("ULTRACEMCO.NS", "UltraTech Cement"),
            Map.entry("TITAN.NS",      "Titan Company"),
            Map.entry("SUNPHARMA.NS",  "Sun Pharmaceutical"),
            Map.entry("TATAMOTORS.NS", "Tata Motors"),
            Map.entry("NTPC.NS",       "NTPC"),
            Map.entry("POWERGRID.NS",  "Power Grid Corporation"),
            Map.entry("TATASTEEL.NS",  "Tata Steel"),
            Map.entry("ONGC.NS",       "Oil & Natural Gas Corporation"),
            Map.entry("BAJAJFINSV.NS", "Bajaj Finserv"),
            Map.entry("GRASIM.NS",     "Grasim Industries"),
            Map.entry("COALINDIA.NS",  "Coal India"),
            Map.entry("DIVISLAB.NS",   "Divi's Laboratories"),
            Map.entry("DRREDDY.NS",    "Dr. Reddy's Laboratories"),
            Map.entry("CIPLA.NS",      "Cipla"),
            Map.entry("EICHERMOT.NS",  "Eicher Motors"),
            Map.entry("HEROMOTOCO.NS", "Hero MotoCorp"),
            Map.entry("BPCL.NS",       "Bharat Petroleum"),
            Map.entry("ADANIPORTS.NS", "Adani Ports"),
            Map.entry("JSWSTEEL.NS",   "JSW Steel"),
            Map.entry("HINDALCO.NS",   "Hindalco Industries"),
            Map.entry("INDUSINDBK.NS", "IndusInd Bank"),
            Map.entry("TECHM.NS",      "Tech Mahindra"),
            Map.entry("SBILIFE.NS",    "SBI Life Insurance"),
            Map.entry("HDFCLIFE.NS",   "HDFC Life Insurance"),
            Map.entry("APOLLOHOSP.NS", "Apollo Hospitals"),
            Map.entry("ADANIENT.NS",   "Adani Enterprises"),
            Map.entry("LTIM.NS",       "LTIMindtree"),
            Map.entry("BAJAJ-AUTO.NS", "Bajaj Auto"),
            Map.entry("BEL.NS",        "Bharat Electronics"),
            Map.entry("TRENT.NS",      "Trent"),
            Map.entry("SHRIRAMFIN.NS", "Shriram Finance"),
            Map.entry("M&M.NS",        "Mahindra & Mahindra"),
            Map.entry("NESTLEIND.NS",  "Nestle India")
    );
}
